package com.project.vortex.callsagent.data.sip

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.Core
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
            }
            _state.value = mapped
        }
    }

    init {
        call.addListener(listener)
        _state.value = mapState(call.state)
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
     * Route the call's output to the device speaker (`true`) or to the
     * earpiece / closest non-speaker output (`false`).
     *
     * Returns `true` if the routing was applied, `false` if the target
     * device type does not exist on this hardware (typical case: the
     * Galaxy Tab A9+ has no `Earpiece` device, only `Speaker`). Callers
     * should keep their UI flag in sync with the returned value so the
     * agent does not see a "phantom" toggle that produces no audible
     * change.
     */
    fun setSpeakerEnabled(enabled: Boolean): Boolean {
        val targetType =
            if (enabled) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        val device = core.audioDevices.firstOrNull { d ->
            d.type == targetType &&
                d.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        }
        if (device == null) {
            Log.w(TAG, "No audio device of type $targetType available")
            return false
        }
        call.outputAudioDevice = device
        Log.d(TAG, "Output device set to ${device.deviceName} ($targetType)")
        return true
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
