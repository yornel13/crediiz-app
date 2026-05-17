package com.project.vortex.callsagent.presentation.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.call.CallController
import com.project.vortex.callsagent.domain.call.model.EndedCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Routing signals for [AppNavGraph]. Two independent reasons to land
 * on Post-Call:
 *
 *  1. **Just-ended call** — the agent finished a call this session.
 *     [endedCall] flips and the graph routes to Post-Call.
 *
 *  2. **Orphan-call recovery (Phase 7.5)** — the app was killed
 *     between call end and Post-Call save. On the next launch we
 *     find the most recent unconfirmed interaction and surface it
 *     so the agent can wrap up. [orphanInteraction] flips once
 *     after init.
 *
 * Both are one-shot signals; consume to clear.
 */
@HiltViewModel
class CallNavigationViewModel @Inject constructor(
    private val callController: CallController,
    private val interactionRepository: InteractionRepository,
    private val clientRepository: ClientRepository,
) : ViewModel() {

    val endedCall: StateFlow<EndedCall?> = callController.lastEndedCall

    fun consumeEndedCall() = callController.consumeLastEndedCall()

    /**
     * Surfaced by [CallController] when the post-call persistence
     * path fails (save throws). The graph collects this and shows
     * the message as a Toast so the agent — and we, when
     * debugging — see WHY the call disappeared instead of just
     * "no aparece en recientes".
     */
    val saveError: StateFlow<String?> = callController.saveError

    fun consumeSaveError() = callController.consumeSaveError()

    /** Recovery target — clientId + interactionMobileSyncId. */
    private val _orphanInteraction = MutableStateFlow<EndedCall?>(null)
    val orphanInteraction: StateFlow<EndedCall?> = _orphanInteraction.asStateFlow()

    init {
        scanForOrphan()
    }

    fun consumeOrphanInteraction() {
        _orphanInteraction.value = null
    }

    /**
     * Runs once at ViewModel construction (i.e. once per app session).
     * Sweeps stale unconfirmed interactions older than the recovery
     * window, then surfaces the most recent unconfirmed one within
     * the window for the graph to route to.
     *
     * **Client-existence guard:** an orphan whose `clientId` no
     * longer exists in the local DB (sync removed it, never landed,
     * etc.) would only show "Couldn't load call details" — useless
     * to the agent. Those rows are silently auto-confirmed and
     * skipped so the agent isn't trapped on a broken wrap-up screen
     * at every cold start until the 24-hour stale sweep gets it.
     * The loop continues to the next-most-recent orphan with a real
     * client; if none exist, the graph never receives an
     * `orphanInteraction` signal.
     */
    private fun scanForOrphan() {
        viewModelScope.launch {
            val now = Instant.now()
            val windowStart = now.minus(RECOVERY_WINDOW_HOURS, ChronoUnit.HOURS)

            // Auto-confirm anything older than the window so we don't
            // re-prompt forever.
            interactionRepository.autoConfirmStale(before = windowStart)

            // Loop: pick the most recent unconfirmed orphan; if its
            // client doesn't exist locally, mark it confirmed (closes
            // it silently) and try again. Stop when we find one with
            // a valid client or run out of orphans.
            while (true) {
                val orphan = interactionRepository
                    .findMostRecentUnconfirmed(since = windowStart) ?: return@launch

                val client = clientRepository.findById(orphan.clientId)
                if (client != null) {
                    _orphanInteraction.value = EndedCall(
                        clientId = orphan.clientId,
                        interactionMobileSyncId = orphan.mobileSyncId,
                    )
                    return@launch
                }

                Log.w(
                    TAG,
                    "Orphan interaction ${orphan.mobileSyncId} references " +
                        "client ${orphan.clientId} that is not in the local DB — " +
                        "auto-confirming and skipping recovery surface.",
                )
                interactionRepository.markConfirmed(orphan.mobileSyncId)
            }
        }
    }

    private companion object {
        const val RECOVERY_WINDOW_HOURS = 24L
        const val TAG = "CallNavViewModel"
    }
}
