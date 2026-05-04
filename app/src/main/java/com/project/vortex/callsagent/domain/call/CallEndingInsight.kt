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
        private val ALL_OUTCOMES = listOf(
            CallOutcome.INTERESTED,
            CallOutcome.NOT_INTERESTED,
            CallOutcome.NO_ANSWER,
            CallOutcome.BUSY,
            CallOutcome.INVALID_NUMBER,
        )

        /** Outcomes that only make sense after a real conversation. */
        private val CONVERSATIONAL_OUTCOMES = listOf(
            CallOutcome.INTERESTED,
            CallOutcome.NOT_INTERESTED,
        )

        /** Outcomes that only make sense when the call never connected. */
        private val NON_CONVERSATIONAL_OUTCOMES = listOf(
            CallOutcome.NO_ANSWER,
            CallOutcome.BUSY,
            CallOutcome.INVALID_NUMBER,
        )

        fun from(ending: SipCallEnding): CallEndingInsight = when (ending) {
            SipCallEnding.Answered -> CallEndingInsight(
                suggestedOutcome = null,
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
                suggestedOutcome = CallOutcome.INVALID_NUMBER,
                allowedOutcomes = listOf(CallOutcome.INVALID_NUMBER),
                reasonLabel = "Número no válido.",
            )

            SipCallEnding.NetworkError -> CallEndingInsight(
                suggestedOutcome = null,
                allowedOutcomes = ALL_OUTCOMES,
                reasonLabel = "Hubo un problema de red durante la llamada.",
            )

            SipCallEnding.Cancelled -> CallEndingInsight(
                suggestedOutcome = null,
                allowedOutcomes = NON_CONVERSATIONAL_OUTCOMES,
                reasonLabel = "Llamada cancelada antes de conectar.",
            )

            is SipCallEnding.Other -> CallEndingInsight(
                suggestedOutcome = null,
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
