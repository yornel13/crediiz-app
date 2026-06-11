package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.data.sip.SipCallEnding

/**
 * UI-facing recommendation derived from a [SipCallEnding].
 *
 *  - [suggestedOutcome] is the CallOutcome PostCall should pre-select.
 *    `null` means "we don't know — let the agent decide".
 *  - [allowedOutcomes] is the subset of `CallOutcome` PostCall should
 *    show as buttons. Hides options that physically could not have
 *    happened (e.g. `INTERESTED` on a call that never connected).
 *  - [reasonLabel] is a short Spanish phrase shown below the outcome
 *    header to explain *why* the options were filtered.
 *
 * All mapping decisions live in [from] — the lookup table is the
 * single source of truth for the post-call UX.
 */
data class CallEndingInsight(
    val suggestedOutcome: CallOutcome?,
    val allowedOutcomes: List<CallOutcome>,
    val reasonLabel: String?,
) {
    companion object {
        /** Outcomes that only make sense after a real conversation. */
        private val CONVERSATIONAL_OUTCOMES = listOf(
            CallOutcome.INTERESTED,
            CallOutcome.SCHEDULED,
            CallOutcome.SOLD,
            CallOutcome.NOT_INTERESTED,
            CallOutcome.DO_NOT_CALL,
            CallOutcome.HAS_LOAN,
            CallOutcome.DECEASED,
            CallOutcome.NOT_APPLICABLE,
        )

        /** Outcomes that only make sense when the call never connected. */
        private val NON_CONVERSATIONAL_OUTCOMES = listOf(
            CallOutcome.NO_ANSWER,
            CallOutcome.BUSY,
            CallOutcome.OUT_OF_SERVICE,
            CallOutcome.VOICEMAIL,
            CallOutcome.WRONG_NUMBER,
        )

        /** All 13 outcomes the backend accepts (FRONTEND-ESTADOS, CallOutcome). */
        private val ALL_OUTCOMES = CONVERSATIONAL_OUTCOMES + NON_CONVERSATIONAL_OUTCOMES

        fun from(ending: SipCallEnding): CallEndingInsight = when (ending) {
            SipCallEnding.Answered -> CallEndingInsight(
                // The call connected but we can't infer the business result.
                // Persist NO_SELECTED as the placeholder (NOT a real choice)
                // so an unclassified answered call stops reporting as
                // NO_ANSWER. PostCall strips it from the pre-selection, so the
                // agent must still pick a real outcome. NO_SELECTED is never in
                // `allowedOutcomes` — it is not a selectable button.
                suggestedOutcome = CallOutcome.NO_SELECTED,
                allowedOutcomes = ALL_OUTCOMES,
                reasonLabel = null,
            )

            SipCallEnding.NotAnswered -> CallEndingInsight(
                suggestedOutcome = CallOutcome.NO_ANSWER,
                allowedOutcomes = NON_CONVERSATIONAL_OUTCOMES,
                reasonLabel = "El cliente no contestó.",
            )

            SipCallEnding.Busy -> CallEndingInsight(
                suggestedOutcome = CallOutcome.BUSY,
                allowedOutcomes = listOf(CallOutcome.BUSY, CallOutcome.NO_ANSWER),
                reasonLabel = "Línea ocupada.",
            )

            SipCallEnding.Declined -> CallEndingInsight(
                suggestedOutcome = CallOutcome.NO_ANSWER,
                allowedOutcomes = listOf(CallOutcome.NO_ANSWER, CallOutcome.BUSY),
                reasonLabel = "El cliente rechazó la llamada.",
            )

            SipCallEnding.InvalidNumber -> CallEndingInsight(
                suggestedOutcome = CallOutcome.WRONG_NUMBER,
                allowedOutcomes = listOf(CallOutcome.WRONG_NUMBER),
                reasonLabel = "Número no válido.",
            )

            // Ambiguous endings (NetworkError / Cancelled / Other) carry NO
            // objective signal about the result — the SIP layer only knows the
            // call dropped/was cancelled, not WHY. Defaulting to NO_ANSWER would
            // fabricate a disposition the agent never chose, so we persist the
            // NO_SELECTED placeholder and force a real classification in PostCall.
            SipCallEnding.NetworkError -> CallEndingInsight(
                suggestedOutcome = CallOutcome.NO_SELECTED,
                allowedOutcomes = ALL_OUTCOMES,
                reasonLabel = "Hubo un problema de red durante la llamada.",
            )

            SipCallEnding.Cancelled -> CallEndingInsight(
                suggestedOutcome = CallOutcome.NO_SELECTED,
                allowedOutcomes = NON_CONVERSATIONAL_OUTCOMES,
                reasonLabel = "Llamada cancelada antes de conectar.",
            )

            is SipCallEnding.Other -> CallEndingInsight(
                suggestedOutcome = CallOutcome.NO_SELECTED,
                allowedOutcomes = ALL_OUTCOMES,
                reasonLabel = ending.reason
                    ?.takeIf { it.isNotBlank() && it != "None" }
                    ?.let { "Código: $it" },
            )
        }

        /**
         * Reconstruct an insight from the `disconnectCause` column
         * persisted on the InteractionEntity (the simple class name
         * of [SipCallEnding]). Used by the orphan-recovery path so
         * that on app restart, PostCall still filters outcomes the
         * same way as a fresh call.
         *
         * Returns `null` if the cause is missing or unrecognized — in
         * that case the caller should fall back to the default "all
         * five outcomes, no label" behavior.
         */
        fun fromPersistedCause(disconnectCause: String?): CallEndingInsight? {
            if (disconnectCause.isNullOrBlank()) return null
            val ending: SipCallEnding = when (disconnectCause) {
                "Answered" -> SipCallEnding.Answered
                "NotAnswered" -> SipCallEnding.NotAnswered
                "Busy" -> SipCallEnding.Busy
                "Declined" -> SipCallEnding.Declined
                "InvalidNumber" -> SipCallEnding.InvalidNumber
                "NetworkError" -> SipCallEnding.NetworkError
                "Cancelled" -> SipCallEnding.Cancelled
                // SIP code / phrase metadata is not persisted — recover
                // the category and rely on the generic "Code: ..." path.
                "Other" -> SipCallEnding.Other(sipCode = null, reason = null)
                else -> return null
            }
            return from(ending)
        }
    }
}
