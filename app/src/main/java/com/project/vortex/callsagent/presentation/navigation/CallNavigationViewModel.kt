package com.project.vortex.callsagent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.telecom.CallManager
import com.project.vortex.callsagent.telecom.model.EndedCall
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
    private val callManager: CallManager,
    private val interactionRepository: InteractionRepository,
) : ViewModel() {

    val endedCall: StateFlow<EndedCall?> = callManager.lastEndedCall

    fun consumeEndedCall() = callManager.consumeLastEndedCall()

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
     */
    private fun scanForOrphan() {
        viewModelScope.launch {
            val now = Instant.now()
            val windowStart = now.minus(RECOVERY_WINDOW_HOURS, ChronoUnit.HOURS)

            // Auto-confirm anything older than the window so we don't
            // re-prompt forever.
            interactionRepository.autoConfirmStale(before = windowStart)

            val orphan = interactionRepository.findMostRecentUnconfirmed(since = windowStart)
            if (orphan != null) {
                _orphanInteraction.value = EndedCall(
                    clientId = orphan.clientId,
                    interactionMobileSyncId = orphan.mobileSyncId,
                )
            }
        }
    }

    private companion object {
        const val RECOVERY_WINDOW_HOURS = 24L
    }
}
