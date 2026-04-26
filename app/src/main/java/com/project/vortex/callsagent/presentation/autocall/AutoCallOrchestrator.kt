package com.project.vortex.callsagent.presentation.autocall

import android.util.Log
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.telecom.CallManager
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
    private val callManager: CallManager,
    private val settingsPreferences: SettingsPreferences,
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
     * persisted. Records the outcome, advances the cursor, sets the
     * "pending auto-call" flag if appropriate, and returns where the UI
     * should navigate next.
     *
     * Returns null when no session is active — caller falls back to its
     * default post-save navigation (Home).
     */
    suspend fun onPostCallSaved(outcome: CallOutcome): AutoCallNavTarget? {
        val current = _session.value ?: run {
            Log.d(TAG, "onPostCallSaved — no session active, returning null")
            return null
        }
        val updatedStats = current.stats.recordOutcome(outcome)

        return if (!current.hasNext) {
            _session.value = current.copy(stats = updatedStats)
            _pendingAutoCall.value = null
            Log.d(TAG, "onPostCallSaved — queue exhausted at index=${current.currentIndex}")
            AutoCallNavTarget.SessionSummary
        } else {
            val nextIndex = current.currentIndex + 1
            val nextClientId = current.queue[nextIndex]
            _session.value = current.copy(
                currentIndex = nextIndex,
                stats = updatedStats,
            )
            val autoAdvanceEnabled = settingsPreferences.autoAdvanceFlow.first()
            val countdown = autoAdvanceEnabled && shouldAutoAdvanceFor(outcome)
            _pendingAutoCall.value =
                if (countdown) PendingAutoCall(clientId = nextClientId) else null
            Log.d(
                TAG,
                "onPostCallSaved outcome=$outcome → cursor=$nextIndex/${current.total} " +
                    "next=$nextClientId countdown=$countdown",
            )
            AutoCallNavTarget.NextClient(nextClientId)
        }
    }

    /** Skip the current client without calling. Returns the next nav
     * target (NextClient or SessionSummary), or null if no session. */
    fun skipCurrent(): AutoCallNavTarget? {
        val current = _session.value ?: return null
        val updatedStats = current.stats.recordSkip()

        return if (!current.hasNext) {
            _session.value = current.copy(stats = updatedStats)
            _pendingAutoCall.value = null
            AutoCallNavTarget.SessionSummary
        } else {
            val nextIndex = current.currentIndex + 1
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
     * outgoing call via [CallManager]. */
    suspend fun fireAutoCall() {
        val pending = _pendingAutoCall.value ?: run {
            Log.w(TAG, "fireAutoCall — no pendingAutoCall, ignoring")
            return
        }
        _pendingAutoCall.value = null
        val client = runCatching { clientRepository.findById(pending.clientId) }
            .getOrNull() ?: run {
                Log.w(TAG, "fireAutoCall — client ${pending.clientId} not found in Room")
                return
            }
        Log.d(TAG, "fireAutoCall — placing call to ${client.id}")
        callManager.startCall(client)
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
     * Auto-advance policy: every outcome triggers the countdown EXCEPT
     * `INTERESTED` — that one earns a stop so the agent can breathe and
     * absorb that they captured a lead (and the follow-up form they just
     * filled out). The other outcomes are decisive enough that we don't
     * want a follow-up reflection — just dial the next.
     *
     * Note: this is a stronger auto-advance than the original MOBILE_APP
     * spec (which limited countdown to NO_ANSWER/BUSY). Field feedback
     * was that stopping on NOT_INTERESTED / INVALID_NUMBER felt like the
     * app gave up on the session.
     */
    private fun shouldAutoAdvanceFor(outcome: CallOutcome): Boolean = when (outcome) {
        CallOutcome.INTERESTED -> false
        CallOutcome.NO_ANSWER,
        CallOutcome.BUSY,
        CallOutcome.NOT_INTERESTED,
        CallOutcome.INVALID_NUMBER -> true
    }

    companion object {
        private const val TAG = "AutoCallOrchestrator"
    }
}
