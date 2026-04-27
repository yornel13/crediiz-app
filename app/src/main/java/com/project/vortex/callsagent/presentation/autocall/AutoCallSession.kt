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
    val interested: Int = 0,
    val notInterested: Int = 0,
    val noAnswer: Int = 0,
    val busy: Int = 0,
    val invalidNumber: Int = 0,
    val skipped: Int = 0,
    val startedAt: Instant,
) {
    val processed: Int
        get() = interested + notInterested + noAnswer + busy + invalidNumber + skipped

    fun recordOutcome(outcome: CallOutcome): AutoCallSessionStats = when (outcome) {
        CallOutcome.INTERESTED -> copy(interested = interested + 1)
        CallOutcome.NOT_INTERESTED -> copy(notInterested = notInterested + 1)
        CallOutcome.NO_ANSWER -> copy(noAnswer = noAnswer + 1)
        CallOutcome.BUSY -> copy(busy = busy + 1)
        CallOutcome.INVALID_NUMBER -> copy(invalidNumber = invalidNumber + 1)
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
