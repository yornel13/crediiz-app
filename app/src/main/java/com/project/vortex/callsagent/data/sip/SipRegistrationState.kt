package com.project.vortex.callsagent.data.sip

/**
 * Domain-level SIP registration state, decoupled from Linphone's
 * [org.linphone.core.RegistrationState] enum so the rest of the app
 * stays SDK-agnostic.
 */
sealed class SipRegistrationState {
    data object Idle : SipRegistrationState()
    data object InProgress : SipRegistrationState()
    data object Registered : SipRegistrationState()
    data object Cleared : SipRegistrationState()
    data class Failed(val message: String) : SipRegistrationState()
}
