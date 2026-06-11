package com.project.vortex.callsagent.data.sip

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Reason
import java.time.Instant

private const val TAG = "CallSession"

/**
 * Domain-level wrapper around a Linphone [Call]. Maps Linphone's
 * granular [Call.State] enum to our coarser [SipCallState] sealed class,
 * and exposes the operations agents need: mute, speaker toggle,
 * hangup, DTMF.
 *
 * Created by [LinphoneCoreManager.placeCall] for outbound calls.
 * One instance per call. After [SipCallState.Disconnected] the wrapper
 * is dead — do not reuse.
 */
class CallSession internal constructor(
    private val call: Call,
    private val core: Core,
) {

    private val _state = MutableStateFlow<SipCallState>(SipCallState.Dialing)
    val state: StateFlow<SipCallState> = _state.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /**
     * Live audio-routing snapshot (active route + available routes).
     * Re-published whenever the device list changes (headset plugged /
     * unplugged, Bluetooth connected / dropped) so the UI can switch
     * between a binary toggle and a device picker on the fly.
     */
    private val _audioRoute = MutableStateFlow(AudioRouteState.SpeakerOnly)
    val audioRoute: StateFlow<AudioRouteState> = _audioRoute.asStateFlow()

    /**
     * The route the agent explicitly picked, or `null` while we follow
     * the automatic policy. A manual pick is honored as long as its
     * device stays available; once it disappears we fall back to auto.
     */
    private var userPickedRoute: AudioRoute? = null

    /**
     * Categorical reason this call ended. `null` while the call is
     * active; populated exactly once when the call transitions to
     * Disconnected. Consumers (CallController) read this to decide
     * which CallOutcome options to surface in PostCall.
     */
    private val _ending = MutableStateFlow<SipCallEnding?>(null)
    val ending: StateFlow<SipCallEnding?> = _ending.asStateFlow()

    /** Captured the first time the call enters Active so the timer is stable
     * across subsequent re-INVITE / Updating transitions. */
    private var activeSince: Instant? = null

    /** True once the call reached StreamsRunning (= the remote answered
     * and audio streams negotiated). Drives the `Answered` ending. */
    private var wasConnected: Boolean = false

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, cstate: Call.State?, message: String) {
            Log.d(TAG, "Call state=$cstate message=$message")
            if (cstate == Call.State.StreamsRunning) {
                wasConnected = true
            }
            val mapped = mapState(cstate)
            // Compute ending BEFORE emitting Disconnected so that any
            // collector observing _state can already read _ending.value.
            if (mapped is SipCallState.Disconnected && _ending.value == null) {
                _ending.value = computeEnding()
                // The Core is a long-lived singleton: detach our listeners
                // exactly once when the call ends so the dead session does
                // not keep reacting to device changes (and leaking).
                runCatching {
                    core.removeListener(coreAudioListener)
                    call.removeListener(this)
                }
            }
            _state.value = mapped
        }
    }

    /**
     * Reacts to audio-device changes on the singleton Core for the life
     * of this call. [onAudioDevicesListUpdated] fires when a headset is
     * plugged/unplugged or Bluetooth connects/drops; we re-apply the
     * routing policy and re-publish. [onAudioDeviceChanged] fires after a
     * route actually switches; we just re-publish the active route.
     */
    private val coreAudioListener = object : CoreListenerStub() {
        override fun onAudioDevicesListUpdated(core: Core) {
            applyPreferredRouteAndPublish()
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            publishRouteState()
        }
    }

    init {
        call.addListener(listener)
        core.addListener(coreAudioListener)
        _state.value = mapState(call.state)
        applyPreferredRouteAndPublish()
    }

    fun setMuted(muted: Boolean) {
        call.microphoneMuted = muted
        _isMuted.value = muted
    }

    /** Send a DTMF tone (RFC 2833 default in Linphone). */
    fun dtmf(digit: Char) {
        call.sendDtmf(digit)
    }

    /**
     * Playback routes this call can use right now, derived from
     * Linphone's live device list, de-duplicated by [AudioRoute] (e.g.
     * Headset + Headphones collapse to one WiredHeadset entry) and
     * ordered for stable rendering.
     */
    fun availableRoutes(): List<AudioRoute> =
        core.audioDevices
            .filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
            .mapNotNull { it.type.toAudioRouteOrNull() }
            .distinct()
            .sortedBy { DISPLAY_ROUTE_ORDER.indexOf(it) }

    /** The route currently carrying call audio, or `null` if unmapped. */
    fun currentRoute(): AudioRoute? =
        call.outputAudioDevice?.type?.toAudioRouteOrNull()

    /**
     * Route the call's output to [route] at the agent's explicit request.
     * Remembers the choice so the auto-switch policy won't override it
     * while that device stays available. Returns `true` if a matching
     * playback device existed and was applied.
     */
    fun selectRoute(route: AudioRoute): Boolean {
        userPickedRoute = route
        val applied = applyRoute(route)
        publishRouteState()
        return applied
    }

    /**
     * Apply the auto-routing policy and publish the result. Honors a
     * still-available manual pick; otherwise the highest-priority
     * connected device wins (Bluetooth > wired > speaker > earpiece).
     * Called on every device-list change and once at call start.
     */
    private fun applyPreferredRouteAndPublish() {
        val device = core.audioDevices.preferredPlaybackDevice(
            preferred = userPickedRoute?.takeIf { it in availableRoutes() },
        )
        if (device != null && device.type.toAudioRouteOrNull() != currentRoute()) {
            call.outputAudioDevice = device
            Log.d(TAG, "Auto-routed to ${device.deviceName} (${device.type})")
        }
        publishRouteState()
    }

    /** Force the call output onto the first playback device of [route]. */
    private fun applyRoute(route: AudioRoute): Boolean {
        val device = core.audioDevices.firstOrNull { d ->
            d.hasCapability(AudioDevice.Capabilities.CapabilityPlay) &&
                d.type.toAudioRouteOrNull() == route
        }
        if (device == null) {
            Log.w(TAG, "No playback device available for route $route")
            return false
        }
        call.outputAudioDevice = device
        Log.d(TAG, "Output routed to ${device.deviceName} ($route)")
        return true
    }

    private fun publishRouteState() {
        _audioRoute.value = AudioRouteState(
            current = currentRoute() ?: AudioRoute.Speaker,
            available = availableRoutes().ifEmpty { listOf(AudioRoute.Speaker) },
        )
    }

    fun disconnect() {
        when (call.state) {
            Call.State.End, Call.State.Released, Call.State.Error -> Unit
            else -> {
                runCatching { call.terminate() }.onFailure {
                    Log.e(TAG, "terminate() threw", it)
                }
            }
        }
    }

    /**
     * Map Linphone's [Call.reason] + SIP protocol code into our coarser
     * [SipCallEnding]. Called exactly once when the session ends.
     *
     * Resolution order (most specific first):
     *  1. If the call was answered (StreamsRunning seen), it's `Answered`.
     *  2. SIP code wins if known — codes carry intent more reliably than
     *     Linphone's enum (e.g. 487 Request Terminated only happens on
     *     CANCEL, regardless of reason).
     *  3. Linphone Reason as fallback.
     *  4. Otherwise, `Other(code, reason)`.
     */
    private fun computeEnding(): SipCallEnding {
        if (wasConnected) return SipCallEnding.Answered

        val reason = call.reason
        val sipCode = call.errorInfo?.protocolCode ?: 0

        when (sipCode) {
            486 -> return SipCallEnding.Busy
            603 -> return SipCallEnding.Declined
            404, 410, 484 -> return SipCallEnding.InvalidNumber
            408 -> return SipCallEnding.NetworkError
            487 -> return SipCallEnding.Cancelled
        }

        return when (reason) {
            Reason.Busy -> SipCallEnding.Busy
            Reason.Declined -> SipCallEnding.Declined
            Reason.NotFound -> SipCallEnding.InvalidNumber
            Reason.NotAnswered -> SipCallEnding.NotAnswered
            Reason.IOError, Reason.ServerTimeout -> SipCallEnding.NetworkError
            Reason.None -> SipCallEnding.Cancelled
            else -> SipCallEnding.Other(
                sipCode = sipCode.takeIf { it > 0 },
                reason = reason?.toString(),
            )
        }
    }

    private fun mapState(state: Call.State?): SipCallState = when (state) {
        Call.State.IncomingReceived,
        Call.State.IncomingEarlyMedia,
        Call.State.OutgoingRinging -> SipCallState.Ringing

        Call.State.OutgoingInit,
        Call.State.OutgoingProgress,
        Call.State.OutgoingEarlyMedia,
        Call.State.PushIncomingReceived -> SipCallState.Dialing

        Call.State.Connected,
        Call.State.StreamsRunning,
        Call.State.UpdatedByRemote,
        Call.State.Updating,
        Call.State.Resuming -> {
            if (activeSince == null) activeSince = Instant.now()
            SipCallState.Active(activeSince!!)
        }

        Call.State.End,
        Call.State.Released,
        Call.State.Error -> SipCallState.Disconnected

        else -> _state.value // Pause/Paused/Idle/etc — preserve current state.
    }
}
