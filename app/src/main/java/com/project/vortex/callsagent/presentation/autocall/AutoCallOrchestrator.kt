package com.project.vortex.callsagent.presentation.autocall

import android.util.Log
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.domain.call.CallController
import com.project.vortex.callsagent.domain.call.CallReadiness
import com.project.vortex.callsagent.domain.call.CallReadinessProvider
import com.project.vortex.callsagent.domain.repository.ClientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates a sequential auto-call session: holds the queue, advances
 * the cursor on each Post-Call save, decides whether to show the 5s
 * countdown overlay on the next Pre-Call, and signals "queue exhausted"
 * so the navigation graph can land on the session-summary screen.
 *
 * Auto-advance policy (Phase 4 default; flip the body of
 * [shouldAutoAdvanceFor] to extend):
 *  - `NO_ANSWER` / `BUSY` → countdown + auto-call.
 *  - `INTERESTED` / `NOT_INTERESTED` / `INVALID_NUMBER` → no countdown,
 *    agent confirms the next call manually with a tap. Rationale:
 *    `INTERESTED` already costs the agent some thinking (follow-up
 *    form); the others are decisive enough that we don't want a
 *    rebound dial that interrupts a quick note.
 *
 * Auto-advance is also gated by [SettingsPreferences.autoAdvanceFlow] —
 * if the agent has the toggle off, no countdown ever fires; the session
 * still advances client by client but each call requires a tap.
 */
@Singleton
class AutoCallOrchestrator @Inject constructor(
    private val clientRepository: ClientRepository,
    private val callController: CallController,
    private val settingsPreferences: SettingsPreferences,
    private val callReadinessProvider: CallReadinessProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<AutoCallSession?>(null)
    val session: StateFlow<AutoCallSession?> = _session.asStateFlow()

    private val _pendingAutoCall = MutableStateFlow<PendingAutoCall?>(null)
    val pendingAutoCall: StateFlow<PendingAutoCall?> = _pendingAutoCall.asStateFlow()

    val isActive: Boolean get() = _session.value != null

    /**
     * Start a new session. Returns the first client ID, or null if the
     * queue is empty. Caller (typically `ClientsScreen` FAB) is responsible
     * for navigating the user to that client's `PreCallScreen`.
     */
    fun startSession(clientIds: List<String>, sourceTab: String): String? {
        if (clientIds.isEmpty()) return null
        _session.value = AutoCallSession(
            queue = clientIds,
            currentIndex = 0,
            sourceTab = sourceTab,
            stats = AutoCallSessionStats(
                total = clientIds.size,
                startedAt = Instant.now(),
            ),
        )
        _pendingAutoCall.value = null
        Log.d(TAG, "startSession — queue=${clientIds.size} first=${clientIds.first()}")
        return clientIds.first()
    }

    /**
     * Called by `PostCallViewModel.save()` after the interaction has been
     * persisted. Records the outcome, advances the cursor to the client
     * AFTER [completedClientId] in the queue, sets the "pending auto-call"
     * flag if appropriate, and returns where the UI should navigate next.
     *
     * **Why we take `completedClientId` instead of trusting `currentIndex`:**
     * the agent can manually deviate from the queue order (tapping a
     * different client in the list while a session is active). When
     * that happens the orchestrator's `currentIndex` is left untouched
     * by the manual navigation — it still points at the position the
     * session started from, not the client actually being called. If we
     * advanced via `currentIndex + 1` we'd jump back to position 1
     * regardless of which client the agent really just finished. By
     * locating `completedClientId` in the queue and advancing from
     * there, the session resumes from the right place even after
     * arbitrary deviations.
     *
     * Returns null when no session is active — caller falls back to its
     * default post-save navigation (Home).
     */
    suspend fun onPostCallSaved(
        completedClientId: String,
        outcome: CallOutcome,
    ): AutoCallNavTarget? {
        val current = _session.value ?: run {
            Log.d(TAG, "onPostCallSaved — no session active, returning null")
            return null
        }
        val updatedStats = current.stats.recordOutcome(outcome)

        // Locate the completed client in the queue. If somehow not found
        // (e.g. the agent navigated to a client outside the session
        // queue), fall back to the stored cursor — best-effort.
        val completedIndex = current.queue.indexOf(completedClientId)
        val resolvedCompletedIndex = if (completedIndex >= 0) {
            completedIndex
        } else {
            Log.w(
                TAG,
                "onPostCallSaved — completed client $completedClientId not in queue " +
                    "(size=${current.total}); falling back to cursor=${current.currentIndex}",
            )
            current.currentIndex
        }
        val nextIndex = resolvedCompletedIndex + 1
        val queueExhausted = nextIndex >= current.queue.size

        return if (queueExhausted) {
            _session.value = current.copy(
                currentIndex = resolvedCompletedIndex,
                stats = updatedStats,
            )
            _pendingAutoCall.value = null
            Log.d(
                TAG,
                "onPostCallSaved — queue exhausted at completed=$completedClientId " +
                    "(index=$resolvedCompletedIndex/${current.total})",
            )
            AutoCallNavTarget.SessionSummary
        } else {
            val nextClientId = current.queue[nextIndex]

            // Order matters: set `_pendingAutoCall` BEFORE `_session`.
            //
            // The split-mode UI observes `_session` to switch the detail
            // pane to the next client. When that observer fires, a brand-new
            // `PreCallViewModel` is created for `nextClientId` and starts
            // collecting `pendingAutoCall`. If we set `_session` first,
            // there's a window where the new VM observes
            // `pendingAutoCall == null` (still its old value), computes
            // `countdownActive = false`, and the countdown never starts —
            // even though we're about to set the right value milliseconds
            // later. Setting `_pendingAutoCall` first guarantees the new
            // VM sees the correct pending state on its very first read.
            val autoAdvanceEnabled = settingsPreferences.autoAdvanceFlow.first()
            val countdown = autoAdvanceEnabled && shouldAutoAdvanceFor(outcome)
            _pendingAutoCall.value =
                if (countdown) PendingAutoCall(clientId = nextClientId) else null
            _session.value = current.copy(
                currentIndex = nextIndex,
                stats = updatedStats,
            )
            Log.d(
                TAG,
                "onPostCallSaved outcome=$outcome completed=$completedClientId " +
                    "(@$resolvedCompletedIndex) → next=$nextClientId (@$nextIndex/" +
                    "${current.total}) countdown=$countdown",
            )
            AutoCallNavTarget.NextClient(nextClientId)
        }
    }

    /**
     * Skip the [skippedClientId] without calling. Returns the next nav
     * target (NextClient or SessionSummary), or null if no session.
     *
     * Takes the skipped client's id explicitly for the same reason
     * [onPostCallSaved] takes `completedClientId`: the agent may have
     * navigated outside the queue order, and `currentIndex` doesn't
     * track that.
     */
    fun skipCurrent(skippedClientId: String): AutoCallNavTarget? {
        val current = _session.value ?: return null
        val updatedStats = current.stats.recordSkip()

        val skippedIndex = current.queue.indexOf(skippedClientId)
        val resolvedSkippedIndex = if (skippedIndex >= 0) {
            skippedIndex
        } else {
            Log.w(
                TAG,
                "skipCurrent — skipped client $skippedClientId not in queue " +
                    "(size=${current.total}); falling back to cursor=${current.currentIndex}",
            )
            current.currentIndex
        }
        val nextIndex = resolvedSkippedIndex + 1
        val queueExhausted = nextIndex >= current.queue.size

        return if (queueExhausted) {
            _session.value = current.copy(
                currentIndex = resolvedSkippedIndex,
                stats = updatedStats,
            )
            _pendingAutoCall.value = null
            AutoCallNavTarget.SessionSummary
        } else {
            val nextClientId = current.queue[nextIndex]
            _session.value = current.copy(
                currentIndex = nextIndex,
                stats = updatedStats,
            )
            // Skips never auto-call the next client — the agent intervened.
            _pendingAutoCall.value = null
            AutoCallNavTarget.NextClient(nextClientId)
        }
    }

    /** Called when the countdown overlay completes. Fires the actual
     * outgoing call via [CallController]. */
    suspend fun fireAutoCall() {
        val pending = _pendingAutoCall.value ?: run {
            Log.w(TAG, "fireAutoCall — no pendingAutoCall, ignoring")
            return
        }
        // Bail before dialing if SIP is no longer ready (un-assignment
        // mid-countdown, REGISTER lost, etc.). The CallController has
        // its own guard; we duplicate it here so the pending state is
        // cleared and the UI banner/CTA stays consistent.
        val readiness = callReadinessProvider.readiness.value
        if (readiness !is CallReadiness.Ready) {
            Log.w(TAG, "fireAutoCall — SIP not ready ($readiness), dropping pending call")
            _pendingAutoCall.value = null
            return
        }
        _pendingAutoCall.value = null
        val client = runCatching { clientRepository.findById(pending.clientId) }
            .getOrNull() ?: run {
                Log.w(TAG, "fireAutoCall — client ${pending.clientId} not found in Room")
                return
            }
        Log.d(TAG, "fireAutoCall — placing call to ${client.id}")
        callController.startCall(client)
    }

    /** Called when the agent taps Cancel on the countdown overlay. */
    fun cancelAutoCall() {
        _pendingAutoCall.value = null
    }

    /** Called by the session-summary screen's Done button, or by any
     * Exit Auto-Call action elsewhere. Clears all session state. */
    fun exit() {
        // Stack trace so we can find any unintended caller. Strip on
        // release once auto-call flow is stable.
        Log.d(
            TAG,
            "exit() — clearing session. caller stack:\n" +
                Throwable().stackTraceToString().lines().take(8).joinToString("\n"),
        )
        _session.value = null
        _pendingAutoCall.value = null
    }

    /**
     * Auto-advance policy: the countdown fires after every outcome
     * **except** [CallOutcome.ANSWERED_SOLD]. A closed sale is the
     * funnel's terminal state (`HOW_IT_WORKS.md §5`) and the agent
     * deserves a beat to confirm details before moving on — auto-
     * dialing the next client right after a "💰 Sold" tap feels
     * disrespectful to the moment.
     *
     * INTERESTED still auto-advances: field feedback showed that
     * stopping there made the session feel "deactivated"; the agent
     * already paused naturally while filling the follow-up form.
     */
    /**
     * Auto-advance policy — currently **always true** as long as the
     * agent has the global `Auto-advance` switch enabled in Settings.
     *
     * Previous behavior excluded [CallOutcome.ANSWERED_SOLD] from the
     * countdown to give the agent a beat after closing a sale, but
     * agent feedback was clear: when auto-call is ON, the expectation
     * is "always jump to the next client, until the queue ends". Any
     * exception was perceived as the system "failing intermittently".
     *
     * The hook stays as a function (not an inlined `true`) so we can
     * reintroduce per-outcome rules later without touching the call
     * site.
     */
    private fun shouldAutoAdvanceFor(outcome: CallOutcome): Boolean = true

    companion object {
        private const val TAG = "AutoCallOrchestrator"
    }
}
