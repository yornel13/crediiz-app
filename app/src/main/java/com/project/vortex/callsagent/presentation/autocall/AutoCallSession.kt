package com.project.vortex.callsagent.presentation.autocall

import com.project.vortex.callsagent.common.enums.CallOutcome
import java.time.Instant

/**
 * State of an active auto-call session. Holds the queue of client IDs,
 * the cursor of the one currently in front of the agent, where we came
 * from (so Exit returns to the right tab), and rolling stats for the
 * session-summary screen.
 */
data class AutoCallSession(
    val queue: List<String>,
    val currentIndex: Int,
    val sourceTab: String,
    val stats: AutoCallSessionStats,
) {
    val currentClientId: String? get() = queue.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex + 1 < queue.size
    val total: Int get() = queue.size
    /** 1-based position, for "auto-call (4 / 48)" badges. */
    val displayPosition: Int get() = currentIndex + 1
}

data class AutoCallSessionStats(
    val total: Int,
    /** Funnel advances. */
    val interested: Int = 0,
    val cited: Int = 0,
    val sold: Int = 0,
    /** No-contact bucket (no answer / busy / out of service / voicemail / wrong number). */
    val noContact: Int = 0,
    /** Removed bucket (not interested / do-not-call / has-loan / deceased / not-applicable). */
    val removed: Int = 0,
    val skipped: Int = 0,
    val startedAt: Instant,
) {
    val processed: Int
        get() = interested + cited + sold + noContact + removed + skipped

    fun recordOutcome(outcome: CallOutcome): AutoCallSessionStats = when (outcome) {
        CallOutcome.INTERESTED -> copy(interested = interested + 1)
        CallOutcome.SCHEDULED -> copy(cited = cited + 1)
        CallOutcome.SOLD -> copy(sold = sold + 1)
        CallOutcome.NO_ANSWER,
        CallOutcome.BUSY,
        CallOutcome.OUT_OF_SERVICE,
        CallOutcome.VOICEMAIL,
        CallOutcome.WRONG_NUMBER -> copy(noContact = noContact + 1)
        CallOutcome.NOT_INTERESTED,
        CallOutcome.DO_NOT_CALL,
        CallOutcome.HAS_LOAN,
        CallOutcome.DECEASED,
        CallOutcome.NOT_APPLICABLE -> copy(removed = removed + 1)
        // Unreachable in practice: NO_SELECTED is never agent-saved (PostCall
        // blocks it). Leave stats untouched rather than miscount it.
        CallOutcome.NO_SELECTED -> this
    }

    fun recordSkip(): AutoCallSessionStats = copy(skipped = skipped + 1)
}

/**
 * What should happen after a Post-Call save inside an auto-call session.
 * Returned by [AutoCallOrchestrator.onPostCallSaved] — the caller drives
 * navigation accordingly.
 */
sealed class AutoCallNavTarget {
    data class NextClient(val clientId: String) : AutoCallNavTarget()
    object SessionSummary : AutoCallNavTarget()
}

/**
 * Set on the orchestrator when the agent should see a "calling next in
 * 5..." countdown overlay on the next PreCall screen. The PreCall reads
 * this and renders the overlay; on completion it calls
 * [AutoCallOrchestrator.fireAutoCall], on cancel
 * [AutoCallOrchestrator.cancelAutoCall].
 */
data class PendingAutoCall(
    val clientId: String,
)
