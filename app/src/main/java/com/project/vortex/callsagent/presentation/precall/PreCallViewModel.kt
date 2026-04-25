package com.project.vortex.callsagent.presentation.precall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class PreCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val noteRepository: NoteRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"]) {
        "PreCallViewModel requires a clientId argument"
    }

    private val _uiState = MutableStateFlow(PreCallUiState())
    val uiState: StateFlow<PreCallUiState> = _uiState.asStateFlow()

    val notes: StateFlow<List<Note>> = noteRepository.observeByClient(clientId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
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
