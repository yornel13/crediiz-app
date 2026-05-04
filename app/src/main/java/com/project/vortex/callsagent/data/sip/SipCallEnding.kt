package com.project.vortex.callsagent.data.sip

/**
 * Categorical reason a [CallSession] terminated. Computed once when
 * the session transitions to [SipCallState.Disconnected] and exposed
 * via `CallSession.ending`.
 *
 * Engine-level — domain code maps this to a UI-friendly recommendation
 * via `CallEndingInsight`. Kept SDK-agnostic so swapping Linphone for
 * another stack in the future does not require UI changes.
 */
sealed class SipCallEnding {
    /** Both parties spoke (the call reached the StreamsRunning state). */
    data object Answered : SipCallEnding()

    /** Rang at the remote side but no one picked up. */
    data object NotAnswered : SipCallEnding()

    /** Remote line was busy (SIP 486 / Linphone Reason.Busy). */
    data object Busy : SipCallEnding()

    /** Remote actively rejected while ringing (SIP 603 / Reason.Declined). */
    data object Declined : SipCallEnding()

    /** Number does not exist or was malformed (SIP 404 / 410 / 484). */
    data object InvalidNumber : SipCallEnding()

    /** Network or transport error: timeout, IOError, route failure. */
    data object NetworkError : SipCallEnding()

    /** Local hangup before the remote answered (we cancelled). */
    data object Cancelled : SipCallEnding()

    /** Catch-all for SIP codes / reasons we did not categorize. */
    data class Other(val sipCode: Int? = null, val reason: String? = null) :
        SipCallEnding()
}
