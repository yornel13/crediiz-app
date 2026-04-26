package com.project.vortex.callsagent.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.project.vortex.callsagent.common.enums.MissedCallReason
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Connection service for the calls-agends dialer. Bound by the OS once
 * we hold `RoleManager.ROLE_DIALER`.
 *
 * - **Outgoing**: returns an active Connection with state passthrough.
 * - **Incoming**: returns a ringing Connection so the OS can deliver it
 *   to our `CallsInCallService` for the Option B accept/reject UI. If
 *   another call is already active, returns a pre-disconnected `BUSY`
 *   Connection and logs a `MissedCallEntity(reason=BUSY_OTHER_CALL)`.
 */
@AndroidEntryPoint
class CallsConnectionService : ConnectionService() {

    @Inject lateinit var callManager: CallManager

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        return OutgoingConnection().apply {
            setInitializing()
            setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
            // Phase 3.0 — flip immediately to active. Real state machine
            // is driven by Call.Callback in CallsInCallService.
            setActive()
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        // Busy guard — one call at a time.
        if (callManager.hasActiveCall()) {
            val phone = request?.address?.schemeSpecificPart.orEmpty()
            callManager.logIncomingMissed(phone, MissedCallReason.BUSY_OTHER_CALL)
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
        }

        return IncomingConnection().apply {
            setRinging()
            setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        }
    }

    private inner class OutgoingConnection : Connection() {
        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        override fun onAbort() {
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
        }

        override fun onHold() = setOnHold()
        override fun onUnhold() = setActive()
    }

    private inner class IncomingConnection : Connection() {
        override fun onAnswer() = setActive()

        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }
    }
}
