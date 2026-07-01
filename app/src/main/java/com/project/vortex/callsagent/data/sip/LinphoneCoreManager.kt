package com.project.vortex.callsagent.data.sip

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RawRes
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.data.sip.auth.SipCredentialsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.RegistrationState
import org.linphone.core.ToneID
import org.linphone.core.TransportType
import org.linphone.mediastream.Factory as MsFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "LinphoneCoreManager"
private const val ITERATE_INTERVAL_MS = 20L

// Teardown budgets. `stop()` runs on logout (off the main thread) but is
// still bounded so a wedged native call can never hang shutdown. Worst
// case is CORE_TEARDOWN + THREAD_JOIN before we abandon the thread.
private const val CORE_TEARDOWN_TIMEOUT_MS = 2_000L
private const val THREAD_JOIN_TIMEOUT_MS = 1_000L

/**
 * Registration TTL. Kept short (10 min, was 3600) so Linphone re-REGISTERs
 * every ~5 min (it refreshes at half-life). That bounds how long a stale NAT
 * binding can linger after a call's RTP activity, and gives the SBC a fresh
 * binding well before the agent dials again.
 */
private const val DEFAULT_EXPIRES_SECONDS = 600

/**
 * SIP keep-alive period (ms). A short CRLF ping keeps the signaling NAT
 * binding open between/after calls, so the UDP registration doesn't go silent
 * and decay. Without it, a call's RTP activity can shift the NAT mapping and
 * the registration is dead until the next foreground refresh.
 */
private const val SIP_KEEPALIVE_PERIOD_MS = 20_000

private const val USER_AGENT_NAME = "calls-agends"
private const val USER_AGENT_VERSION = "1.0"

/** RTP local port range — matches Sipnetic's production config. */
private const val RTP_PORT_MIN = 16384
private const val RTP_PORT_MAX = 65535

/**
 * Codec MIME types in priority order. Matches Sipnetic minus G.729
 * (not bundled in the Linphone Android SDK — licensed codec).
 */
private val PREFERRED_AUDIO_CODECS = listOf("opus", "G722", "PCMA", "PCMU")

/** Cap the Opus bit rate to match Sipnetic's `Limit bit rate` setting. */
private const val OPUS_MAX_BITRATE_KBPS = 32

/**
 * Device models whose Qualcomm/Samsung audio HAL has no MMAP mixer path
 * for the `voip-headphones` usecase. On these, Linphone's default AAudio
 * (MMAP / low-latency) backend opens the call output stream but the HAL
 * cannot start the PCM (`mmap-playback voip-headphones` path not found),
 * so call audio is silent on a WIRED HEADSET once the full-duplex stream
 * starts — ringback plays, then goes mute on answer. Speaker / earpiece /
 * Bluetooth are unaffected. Forcing the OpenSLES backend (whose
 * `low-latency-playback voip-headphones` path the HAL does define) fixes
 * it. Confirmed via audio_route HAL logs. See linphone-android #1918.
 */
private val CRAPPY_AAUDIO_MODELS = setOf("SM-X216B") // Galaxy Tab A9+

/**
 * Owns the singleton Linphone [Core], runs its event loop on a dedicated
 * thread, and exposes registration state to the rest of the app.
 *
 * Lifecycle: [start] is idempotent; safe to call from any thread or
 * lifecycle hook. [stop] tears the Core down on process shutdown.
 *
 * Phase 1 (M1) scope:
 *  - Initialize [Core].
 *  - Configure a single account from [SipCredentialsProvider].
 *  - REGISTER over UDP, no SRTP, no STUN — minimal config to validate
 *    that credentials and reachability work end-to-end.
 *
 * SRTP/SDES, codec priority, NAT policy, and call orchestration land in
 * Phase 3 once REGISTER is proven against Voselia.
 */
/**
 * Construction is intentionally plain: this class has no DI annotations.
 * The wiring (Hilt `@Provides`, application context binding, lifecycle
 * scope) lives in [com.project.vortex.callsagent.di.SipModule]. See
 * `docs/SIP_ENGINE_BOUNDARIES.md` for the contract.
 */
class LinphoneCoreManager(
    private val context: Context,
    private val credentialsProvider: SipCredentialsProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var core: Core? = null
    private var iterateThread: HandlerThread? = null
    private var iterateHandler: Handler? = null

    private val _registrationState =
        MutableStateFlow<SipRegistrationState>(SipRegistrationState.Idle)
    val registrationState: StateFlow<SipRegistrationState> =
        _registrationState.asStateFlow()

    /**
     * The account whose registration we currently track. Set when a fresh
     * account is added in [applyAccountAndRegister]. Used to DROP late events
     * from a previously-cleared account: `register()` calls `clearAccounts()`,
     * which fires a `Cleared` event for the OLD account asynchronously — and
     * that event can land AFTER the NEW account's `Ok`, overwriting it and
     * pinning the UI on "Connecting" forever. Volatile: written and read on the
     * iterate thread, but published for safety.
     */
    @Volatile
    private var activeAccount: Account? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String,
        ) {
            val tracked = activeAccount
            if (tracked != null && account != tracked) {
                // Stale event from a prior (cleared) account — never let it
                // clobber the current account's registration state.
                Log.d(TAG, "Ignoring stale registration event ($state) from old account")
                return
            }
            Log.d(TAG, "Registration state=$state message=$message")
            _registrationState.value = when (state) {
                RegistrationState.None, null -> SipRegistrationState.Idle
                RegistrationState.Progress, RegistrationState.Refreshing ->
                    SipRegistrationState.InProgress
                RegistrationState.Ok -> SipRegistrationState.Registered
                RegistrationState.Cleared -> SipRegistrationState.Cleared
                RegistrationState.Failed -> SipRegistrationState.Failed(message)
            }
        }
    }

    /** Idempotent — safe to call repeatedly. */
    @Synchronized
    fun start() {
        if (core != null) return
        Log.d(TAG, "Starting Linphone Core")

        val factory = Factory.instance()
        // Native logs to logcat for now; Phase 8 routes them to a file.
        factory.setDebugMode(true, "LinphoneSDK")

        core = factory.createCore(null, null, context).apply {
            // Outbound only — no inbound INVITE handling in v1.
            isVideoCaptureEnabled = false
            isVideoDisplayEnabled = false

            // Match Sipnetic's audio processing.
            isEchoCancellationEnabled = true
            isMicEnabled = true

            // SRTP with SDES — Voselia accepts plain RTP but Sipnetic
            // uses SRTP in production. We match for parity and to keep
            // the future TLS upgrade path open.
            mediaEncryption = MediaEncryption.SRTP
            isMediaEncryptionMandatory = false  // accept fallback if SBC negotiates plain RTP

            // RTP local port range — anti-NAT, matches Sipnetic.
            setAudioPortRange(RTP_PORT_MIN, RTP_PORT_MAX)

            // Keep the SIP signaling NAT binding alive between calls. Over UDP
            // a silent socket's NAT mapping decays (often within 30-120s),
            // which silently kills the registration after a call's RTP burst.
            // A short CRLF keep-alive holds the binding open.
            isKeepAliveEnabled = true
            val cfg = config
            if (cfg != null) {
                cfg.setInt("sip", "keepalive_period", SIP_KEEPALIVE_PERIOD_MS)
            } else {
                Log.w(TAG, "Core.config is null — SIP keepalive_period not set")
            }

            // Identify ourselves clearly in SIP signaling.
            setUserAgent(USER_AGENT_NAME, USER_AGENT_VERSION)

            // Replace Linphone's default (loud) call tones with softened
            // copies. The setters need filesystem paths, so we stage the
            // bundled res/raw wavs into filesDir first.
            stageRawWav(R.raw.ringback_soft, "ringback_soft.wav")?.let { ringback = it }
            stageRawWav(R.raw.call_end_soft, "call_end_soft.wav")?.let {
                setTone(ToneID.CallEnd, it)
            }

            addListener(coreListener)
            start()

            configureCodecPriority(this)
            applyAAudioWorkaroundIfNeeded(this)
        }

        startIterateLoop()
    }

    /**
     * Stage a bundled wav (`res/raw`) into the app's files dir and return
     * its absolute path, or `null` on failure. Linphone's `ringback` and
     * `setTone` setters need a filesystem path, not a resource id. Copied
     * on every Core start (cheap, a few KB) so a rebuilt wav always wins
     * over a previously staged one.
     */
    private fun stageRawWav(@RawRes resId: Int, fileName: String): String? {
        val outFile = File(context.filesDir, fileName)
        return runCatching {
            context.resources.openRawResource(resId).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
        }.onFailure { Log.e(TAG, "Failed to stage wav $fileName", it) }.getOrNull()
    }

    /**
     * Work around the wired-headset silent-call bug on
     * [CRAPPY_AAUDIO_MODELS] by tagging the running device with
     * [MsFactory.DEVICE_HAS_CRAPPY_AAUDIO], which makes mediastreamer fall
     * back to OpenSLES instead of AAudio. The built-in echo-canceller
     * flags are re-asserted so AEC keeps working under OpenSLES. No-op on
     * other models, which keep the lower-latency AAudio path.
     */
    private fun applyAAudioWorkaroundIfNeeded(core: Core) {
        if (Build.MODEL !in CRAPPY_AAUDIO_MODELS) return
        val msFactory = core.mediastreamerFactory
        val flags = msFactory.deviceFlags or
            MsFactory.DEVICE_HAS_BUILTIN_AEC or
            MsFactory.DEVICE_HAS_BUILTIN_OPENSLES_AEC or
            MsFactory.DEVICE_HAS_CRAPPY_AAUDIO
        msFactory.setDeviceInfo(Build.MANUFACTURER, Build.MODEL, "", flags, 0, 0)
        // Belt-and-suspenders: hard-disable the AAudio sound filters too,
        // so the OpenSLES fallback is used even if the device-info match
        // above doesn't take. Both directions are disabled together to keep
        // capture and playback on the same backend (shared echo canceller).
        runCatching {
            msFactory.enableFilterFromName("MSAAudioPlayer", false)
            msFactory.enableFilterFromName("MSAAudioRecorder", false)
        }.onFailure { Log.w(TAG, "Could not disable AAudio filters", it) }
        core.reloadSoundDevices()
        Log.i(
            TAG,
            "AAudio MMAP workaround applied — forcing OpenSLES for " +
                "${Build.MANUFACTURER}/${Build.MODEL} (flags=$flags)",
        )
    }

    /**
     * Filter the audio codec list to only what we want offered in INVITE
     * SDP. Linphone's default ordering already puts Opus first, then
     * G.722, PCMA, PCMU — same priority as Sipnetic — so we only need to
     * disable the rest.
     *
     * Confirm the codec actually negotiated by inspecting the SDP in
     * the SBC's `200 OK` response (M3).
     */
    private fun configureCodecPriority(core: Core) {
        val current = core.audioPayloadTypes
        val byMime = current.associateBy { it.mimeType.lowercase() }
        val preferred = PREFERRED_AUDIO_CODECS.mapNotNull { byMime[it.lowercase()] }

        current.forEach { it.enable(false) }
        preferred.forEach { it.enable(true) }

        preferred.firstOrNull { it.mimeType.equals("opus", ignoreCase = true) }
            ?.normalBitrate = OPUS_MAX_BITRATE_KBPS

        Log.d(
            TAG,
            "Enabled codecs: " +
                preferred.joinToString { "${it.mimeType}/${it.clockRate}@${it.normalBitrate}kbps" },
        )
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "Stopping Linphone Core")
        val handler = iterateHandler
        val thread = iterateThread
        val liveCore = core

        if (handler != null && thread != null && liveCore != null) {
            // The native Core is NOT thread-safe. Running iterate() on the
            // iterate thread while removeListener()/stop() run on the caller
            // thread races inside liblinphone and trips ART's JNI abort ->
            // SIGABRT. Halt the ticks, then tear the Core down ON the same
            // thread that runs iterate() so the two never overlap.
            handler.removeCallbacksAndMessages(null)
            val torndown = CountDownLatch(1)
            handler.post {
                try {
                    liveCore.removeListener(coreListener)
                    liveCore.stop()
                } catch (t: Throwable) {
                    Log.e(TAG, "Core teardown threw", t)
                } finally {
                    torndown.countDown()
                }
            }
            if (!torndown.await(CORE_TEARDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Core teardown timed out; quitting iterate thread anyway")
            }
            // Wait for the thread to actually die before dropping the Core
            // reference — quitSafely() alone is async and would leave an
            // in-flight tick touching a Core we're about to null out.
            thread.quitSafely()
            try {
                thread.join(THREAD_JOIN_TIMEOUT_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        iterateHandler = null
        iterateThread = null
        core = null
        activeAccount = null
        _registrationState.value = SipRegistrationState.Idle
    }

    /**
     * Configure the SIP account and trigger REGISTER. Calls [start]
     * internally if the Core isn't running. Phase A only ever has one
     * account; we clear and re-add to keep the flow idempotent across
     * dev re-tests.
     */
    fun register() {
        start()
        scope.launch {
            val cfg = credentialsProvider.current()
            withCore { core -> applyAccountAndRegister(core, cfg) }
        }
    }

    private fun applyAccountAndRegister(core: Core, cfg: SipConfig) {
        val identityAddress = Factory.instance().createAddress(cfg.identity)
        val proxyAddress = Factory.instance().createAddress("sip:${cfg.server}")
            ?.apply { transport = TransportType.Udp }

        if (identityAddress == null || proxyAddress == null) {
            Log.e(TAG, "Failed to build SIP addresses for ${cfg.identity}")
            _registrationState.value =
                SipRegistrationState.Failed("Invalid identity or proxy address")
            return
        }

        val authInfo = Factory.instance().createAuthInfo(
            /* username = */ cfg.user,
            /* userid   = */ null,
            /* password = */ cfg.password,
            /* ha1      = */ null,
            /* realm    = */ null,
            /* domain   = */ cfg.server,
        )

        val accountParams = core.createAccountParams().apply {
            setIdentityAddress(identityAddress)
            serverAddress = proxyAddress
            isRegisterEnabled = true
            expires = DEFAULT_EXPIRES_SECONDS
        }
        val account = core.createAccount(accountParams)

        // CRITICAL ordering: point activeAccount at the NEW account BEFORE
        // clearing the old one. clearAccounts() fires a `Cleared` event for the
        // previous account — possibly synchronously — and with activeAccount
        // already updated, the listener reliably drops it as stale instead of
        // letting it race the new account's `Ok` and pin the UI on "Connecting".
        // The new account is not added yet, so clearAccounts() won't touch it.
        activeAccount = account

        core.clearAccounts()
        core.clearAllAuthInfo()
        core.addAuthInfo(authInfo)
        core.addAccount(account)
        core.defaultAccount = account
        _registrationState.value = SipRegistrationState.InProgress
        Log.d(TAG, "REGISTER kicked off for ${cfg.identity}")
    }

    /**
     * Bring the registration back to healthy WITHOUT tearing the account down.
     * If an account is already configured we just re-send REGISTER
     * ([Core.refreshRegisters]) — cheap, reuses the same binding, and avoids
     * the clear/re-add race. Only when there is no account do we fall back to a
     * full [register]. This is the recovery entry point used by the
     * [com.project.vortex.callsagent.domain.call.RegistrationWatchdog].
     */
    fun ensureRegistered() {
        start()
        scope.launch {
            val cfg = runCatching { credentialsProvider.current() }.getOrNull()
            if (cfg == null) {
                Log.w(TAG, "ensureRegistered skipped — no credentials cached")
                return@launch
            }
            withCore { core ->
                if (core.defaultAccount != null) {
                    if (_registrationState.value !is SipRegistrationState.Registered) {
                        _registrationState.value = SipRegistrationState.InProgress
                    }
                    core.refreshRegisters()
                    Log.d(TAG, "refreshRegisters() — recovering registration")
                } else {
                    applyAccountAndRegister(core, cfg)
                }
            }
        }
    }

    /**
     * Place an outbound call to a phone number routed through the
     * registered Voselia account. The number is wrapped as
     * `sip:<number>@<server>`; for raw SIP URIs, pass the full string
     * (begins with `sip:`).
     *
     * Returns a [CallSession] wrapping the live Linphone call, or null
     * if the address is invalid or the Core isn't running.
     */
    suspend fun placeCall(numberOrUri: String): CallSession? {
        start()
        val cfg = credentialsProvider.current()
        val raw = numberOrUri.trim()
        val target = if (raw.startsWith("sip:")) {
            raw
        } else {
            "sip:${normalizePhoneNumber(raw)}@${cfg.server}"
        }
        return suspendCancellableCoroutine { cont ->
            withCore { core ->
                applyPreferredRingbackRoute(core)

                val toAddress = Factory.instance().createAddress(target)
                val call = if (toAddress != null) core.inviteAddress(toAddress) else null
                if (call == null) {
                    Log.e(TAG, "Failed to invite $target")
                }
                if (cont.isActive) {
                    cont.resume(call?.let { CallSession(it, core) })
                }
            }
        }
    }

    /**
     * Pick the initial playback route for the ringback tone, which plays
     * on the Core's output device *before* the per-call media (and thus
     * [CallSession]) exists. Honors a connected Bluetooth / wired headset
     * first, falling back to the built-in speaker — the Tab A9+ hands-free
     * default, where ringback on the earpiece stream is inaudible.
     *
     * Once the call object exists, [CallSession] takes over routing on the
     * call's own output device and reacts to hot-swaps.
     */
    private fun applyPreferredRingbackRoute(core: Core) {
        val device = core.audioDevices.preferredPlaybackDevice()
        if (device != null) {
            core.outputAudioDevice = device
            Log.d(TAG, "Ringback routed to ${device.deviceName} (${device.type})")
        } else {
            Log.w(TAG, "No playback device found for ringback; using default")
        }
    }

    /** Strip spaces, dashes, parentheses; keep `+` for E.164 prefix. */
    private fun normalizePhoneNumber(raw: String): String =
        raw.filter { it == '+' || it.isDigit() }

    /**
     * Run [block] on the Core's iterate thread so we don't race with
     * the SDK's internal state. No-ops if the Core hasn't started.
     */
    private fun withCore(block: (Core) -> Unit) {
        val handler = iterateHandler ?: return
        handler.post {
            val c = core ?: return@post
            block(c)
        }
    }

    private fun startIterateLoop() {
        val thread = HandlerThread("linphone-iterate").apply { start() }
        val handler = Handler(thread.looper)
        iterateThread = thread
        iterateHandler = handler

        val tick = object : Runnable {
            override fun run() {
                try {
                    core?.iterate()
                } catch (t: Throwable) {
                    Log.e(TAG, "iterate() threw", t)
                }
                handler.postDelayed(this, ITERATE_INTERVAL_MS)
            }
        }
        handler.post(tick)
    }
}
