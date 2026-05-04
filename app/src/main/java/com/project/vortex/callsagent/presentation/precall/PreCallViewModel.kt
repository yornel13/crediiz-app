package com.project.vortex.callsagent.presentation.precall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.ClientDismissalRepository
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import com.project.vortex.callsagent.presentation.autocall.AutoCallNavTarget
import com.project.vortex.callsagent.presentation.autocall.AutoCallOrchestrator
import com.project.vortex.callsagent.domain.call.CallController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class PreCallUiState(
    val isLoading: Boolean = true,
    val client: Client? = null,
    val errorMessage: String? = null,
    val isSubmittingNote: Boolean = false,
    val isNoteSheetOpen: Boolean = false,
)

/**
 * One-shot events emitted by the ViewModel that the screen relays to nav.
 */
sealed interface PreCallEvent {
    /** Auto-call: skip this client, go to the next one's PreCall. */
    data class SkipToNext(val clientId: String) : PreCallEvent

    /** Auto-call: skip exhausted the queue → land on session summary. */
    data object SkipToSummary : PreCallEvent

    /** Auto-call: agent tapped "Exit Auto-Call" or back button. */
    data object ExitAutoCall : PreCallEvent

    /** Agent dismissed the client from the detail; pop back. */
    data object Dismissed : PreCallEvent
}

@HiltViewModel
class PreCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val clientDismissalRepository: ClientDismissalRepository,
    private val noteRepository: NoteRepository,
    private val followUpRepository: FollowUpRepository,
    private val syncScheduler: SyncScheduler,
    private val callController: CallController,
    private val autoCallOrchestrator: AutoCallOrchestrator,
    settingsPreferences: SettingsPreferences,
) : ViewModel() {

    /** Live auto-call session state — renders the badge + Skip button. */
    val autoCallSession = autoCallOrchestrator.session

    /** Live "pending auto-call" — drives the countdown overlay. */
    val pendingAutoCall = autoCallOrchestrator.pendingAutoCall

    /**
     * Agent-configured countdown delay (seconds). 0 disables the
     * overlay entirely — the dial fires immediately.
     */
    val autoCallDelaySeconds: StateFlow<Int> = settingsPreferences.autoCallDelayFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsPreferences.DEFAULT_AUTO_CALL_DELAY,
        )

    /**
     * Place the outgoing call via the native Telecom path. The
     * `CallsInCallService` picks it up and launches `InCallActivity`.
     */
    fun startCall() {
        val client = _uiState.value.client ?: return
        // Manual Call cancels any pending auto-call countdown — the agent
        // explicitly took control.
        autoCallOrchestrator.cancelAutoCall()
        callController.startCall(client)
    }

    /** Auto-call countdown finished — fire the next call. */
    fun onAutoCallCountdownComplete() {
        viewModelScope.launch {
            autoCallOrchestrator.fireAutoCall()
        }
    }

    fun cancelAutoCall() = autoCallOrchestrator.cancelAutoCall()

    /**
     * Dismiss the client currently shown in the detail. Records the
     * dismissal locally (status mutation + audit event), cancels any
     * pending auto-call countdown, and signals the screen to pop back.
     */
    fun dismissClient(reasonCode: DismissalReasonCode?, freeFormReason: String?) {
        autoCallOrchestrator.cancelAutoCall()
        viewModelScope.launch {
            clientDismissalRepository.dismiss(clientId, reasonCode, freeFormReason)
            _events.send(PreCallEvent.Dismissed)
        }
    }

    /** Skip the current client (auto-call only). Navigates to next PreCall
     * or to the session summary if the queue is exhausted. */
    fun skipCurrent() {
        viewModelScope.launch {
            when (val target = autoCallOrchestrator.skipCurrent()) {
                is AutoCallNavTarget.NextClient ->
                    _events.send(PreCallEvent.SkipToNext(target.clientId))
                AutoCallNavTarget.SessionSummary ->
                    _events.send(PreCallEvent.SkipToSummary)
                null -> { /* no session — nothing to skip */ }
            }
        }
    }

    /** Exit the active auto-call session (Back button or explicit Exit). */
    fun exitAutoCall() {
        autoCallOrchestrator.exit()
        viewModelScope.launch {
            _events.send(PreCallEvent.ExitAutoCall)
        }
    }

    private val clientId: String = checkNotNull(savedStateHandle["clientId"]) {
        "PreCallViewModel requires a clientId argument"
    }

    private val _uiState = MutableStateFlow(PreCallUiState())
    val uiState: StateFlow<PreCallUiState> = _uiState.asStateFlow()

    private val _events = Channel<PreCallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val notes: StateFlow<List<Note>> = noteRepository.observeByClient(clientId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Next pending follow-up for this client (after now). Drives the
     * "Scheduled call" card on Pre-Call so the agent sees there's a
     * commitment lined up. Captured once on ViewModel construction —
     * a follow-up scheduled today won't go stale during this session.
     */
    val nextFollowUp: StateFlow<FollowUp?> =
        followUpRepository.observeNextPendingForClient(clientId, Instant.now())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    init {
        loadClient()
    }

    fun openNoteSheet() = _uiState.update { it.copy(isNoteSheetOpen = true) }

    fun dismissNoteSheet() = _uiState.update { it.copy(isNoteSheetOpen = false) }

    fun saveManualNote(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || _uiState.value.isSubmittingNote) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingNote = true) }

            val note = Note(
                mobileSyncId = UUID.randomUUID().toString(),
                clientId = clientId,
                interactionMobileSyncId = null,
                content = trimmed,
                type = NoteType.MANUAL,
                deviceCreatedAt = Instant.now(),
                syncStatus = SyncStatus.PENDING,
            )

            runCatching {
                noteRepository.save(note)
                // Mirror onto the denormalized lastNote on Client. Subject to KI-01
                // (next refresh may overwrite); the canonical record is the
                // NoteEntity itself and observeByClient is unaffected.
                clientRepository.updateLastNoteLocally(clientId, trimmed)
            }
                .onSuccess {
                    syncScheduler.triggerImmediateSync()
                    _uiState.update {
                        it.copy(
                            isSubmittingNote = false,
                            isNoteSheetOpen = false,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmittingNote = false,
                            errorMessage = err.message ?: "Could not save note",
                        )
                    }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun loadClient() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val found = runCatching { clientRepository.findById(clientId) }.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    client = found,
                    errorMessage = if (found == null) "Client not found" else null,
                )
            }
        }
    }
}
