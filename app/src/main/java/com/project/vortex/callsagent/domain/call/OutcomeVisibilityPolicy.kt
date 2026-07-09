package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.funnelLevel

/**
 * The ONLY outcomes that advance a client up the sales funnel, mapped to the
 * status each one reaches. Single source of truth shared by
 * `ClientRepositoryImpl` (which applies these advances optimistically as a
 * high-water-mark mirror) and [OutcomeVisibilityPolicy] (which hides advances
 * that would be redundant for the client's current funnel level).
 *
 * Every other outcome — no-contact, hard removal reasons, NOT_INTERESTED,
 * NO_SELECTED — returns `null`: the backend owns those transitions via
 * thresholds / quorum, so the app never guesses them locally.
 */
val CallOutcome.advanceTargetStatus: ClientStatus?
    get() = when (this) {
        CallOutcome.INTERESTED -> ClientStatus.INTERESTED
        CallOutcome.SCHEDULED -> ClientStatus.CITED
        CallOutcome.SOLD -> ClientStatus.CONVERTED
        else -> null
    }

/**
 * Contextual, purely-cosmetic filter for the PostCall outcome selector.
 *
 * `CallEndingInsight` already decided what *physically* could have happened on
 * the call (answered / busy / no-answer …). This policy runs AFTER that,
 * subtracting the outcomes that make no sense for the client's CURRENT
 * position in the monotonic funnel:
 *
 *  - a funnel advance whose target level is `<=` the client's level — re-doing
 *    a rung the client already passed is an idempotent no-op (offering
 *    "Interested" to an already-INTERESTED client, or "Scheduled" to a CITED
 *    one);
 *  - `NOT_INTERESTED` once the client advanced past PENDING — the high-water-
 *    mark model cannot downgrade, so offering it would imply an impossible
 *    move (product decision: hide it rather than record a no-effect outcome).
 *
 * Everything else is untouched and always visible: hard removal reasons
 * (DO_NOT_CALL, WRONG_NUMBER, HAS_LOAN, DECEASED, NOT_APPLICABLE), no-contact /
 * technical outcomes (governed solely by the SIP insight), and NO_SELECTED.
 *
 * The filter never validates business rules — it only tidies the UI. It is a
 * pure, idempotent, order-preserving function; it can only SUBTRACT from
 * [allowed]; it fails open on an unknown status; and it never returns an empty
 * list (falls back to [allowed] if it ever would).
 */
object OutcomeVisibilityPolicy {

    fun contextualAllowedOutcomes(
        allowed: List<CallOutcome>,
        status: ClientStatus?,
    ): List<CallOutcome> {
        // Fail open: with no known status there is no level to compare against.
        status ?: return allowed
        // REMOVED (funnelLevel == null) is lateral / off-funnel; treat it as
        // the bottom so a removed client can be revived by any advance.
        val level = status.funnelLevel ?: 0

        val filtered = allowed.filterNot { outcome ->
            val advanceLevel = outcome.advanceTargetStatus?.funnelLevel
            val redundantAdvance = advanceLevel != null && advanceLevel <= level
            val impossibleDowngrade =
                outcome == CallOutcome.NOT_INTERESTED && level > 0
            redundantAdvance || impossibleDowngrade
        }

        // Defensive: a filter should never strand the agent with no choices.
        return filtered.ifEmpty { allowed }
    }
}
