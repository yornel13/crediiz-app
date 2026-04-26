package com.project.vortex.callsagent.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.project.vortex.callsagent.presentation.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * In-call service for the calls-agends dialer. Bound by the OS while a
 * `Call` is active and we hold `RoleManager.ROLE_DIALER`.
 *
 * Forks on `Call.Details.callDirection`:
 *  - **Outgoing**: forces speaker, hands the call to `CallManager`.
 *  - **Incoming**: hands to `CallManager.setIncomingCall`, which looks
 *    up the caller's client and surfaces the Option B accept/reject UI.
 *
 * Either path launches `InCallActivity`, which renders the right Compose
 * tree based on `CallManager.callDirection` + `callState`.
 */
@AndroidEntryPoint
class CallsInCallService : InCallService() {

    @Inject lateinit var callManager: CallManager

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        if (isIncoming) {
            callManager.setIncomingCall(call, this)
            // Don't force speaker yet — call is still ringing. Speaker is
            // applied on Accept (CallManager.acceptIncoming).
        } else {
            callManager.setOutgoingCall(call, this)
            @Suppress("DEPRECATION")
            runCatching { setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
        }

        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callManager.onCallEnded(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        callManager.onAudioStateChanged(audioState)
    }
}
