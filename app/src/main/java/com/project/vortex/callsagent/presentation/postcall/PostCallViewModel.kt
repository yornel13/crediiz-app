package com.project.vortex.callsagent.presentation.postcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.model.Interaction
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import com.project.vortex.callsagent.presentation.autocall.AutoCallNavTarget
import com.project.vortex.callsagent.presentation.autocall.AutoCallOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class PostCallUiState(
    val isLoading: Boolean = true,
    val client: Client? = null,
    val interaction: Interaction? = null,
    val selectedOutcome: CallOutcome? = null,
    val noteText: String = "",
    val followUpDate: LocalDate? = null,
    val followUpTime: LocalTime? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val showFollowUpForm: Boolean
        get() = selectedOutcome == CallOutcome.INTERESTED

    val canSave: Boolean
        get() {
            if (isSaving || isLoading) return false
            if (selectedOutcome == null) return false
            return if (selectedOutcome == CallOutcome.INTERESTED) {
                followUpDate != null &&
                    followUpTime != null &&
                    isFollowUpInFuture()
            } else true
        }

    val followUpDateTimeError: String?
        get() = if (selectedOutcome == CallOutcome.INTERESTED &&
            followUpDate != null &&
            followUpTime != null &&
            !isFollowUpInFuture()
        ) {
            "Follow-up must be in the future."
        } else null

    private fun isFollowUpInFuture(): Boolean {
        val date = followUpDate ?: return false
        val time = followUpTime ?: return false
        val instant = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
        return instant.isAfter(Instant.now())
    }
}

sealed interface PostCallEvent {
    /** Default save (no auto-call session) — caller pops back to Home. */
    data object Saved : PostCallEvent

    /** Auto-call session active and queue still has more clients. */
    data class SavedNextInSession(val clientId: String) : PostCallEvent

    /** Auto-call session active and queue exhausted. */
    data object SavedSessionComplete : PostCallEvent
}

@HiltViewModel
class PostCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val interactionRepository: InteractionRepository,
    private val noteRepository: NoteRepository,
    private val followUpRepository: FollowUpRepository,
    private val syncScheduler: SyncScheduler,
    private val autoCallOrchestrator: AutoCallOrchestrator,
) : ViewModel() {

    val autoCallSession = autoCallOrchestrator.session

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])
    private val interactionId: String = checkNotNull(savedStateHandle["interactionId"])

    private val prefilledOutcome: CallOutcome? =
        savedStateHandle.get<String>("prefilledOutcome")?.let {
            runCatching { CallOutcome.valueOf(it) }.getOrNull()
        }

    private val _uiState = MutableStateFlow(PostCallUiState())
    val uiState: StateFlow<PostCallUiState> = _uiState.asStateFlow()

    private val _events = Channel<PostCallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun selectOutcome(outcome: CallOutcome) =
        _uiState.update { it.copy(selectedOutcome = outcome) }

    fun onNoteChange(text: String) = _uiState.update { it.copy(noteText = text) }

    fun onFollowUpDateChange(date: LocalDate) =
        _uiState.update { it.copy(followUpDate = date) }

    fun onFollowUpTimeChange(time: LocalTime) =
        _uiState.update { it.copy(followUpTime = time) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val outcome = state.selectedOutcome ?: return
        val interaction = state.interaction ?: return
        val zone = ZoneId.systemDefault()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            runCatching {
                // 1. Update the interaction with the final outcome.
                interactionRepository.save(
                    interaction.copy(
                        outcome = outcome,
                        syncStatus = SyncStatus.PENDING,
                    ),
                )

                // 2. Mirror locally on the client row (status + lastOutcome + counters).
                clientRepository.applyInteractionLocally(
                    clientId = clientId,
                    outcome = outcome,
                    callStartedAt = interaction.callStartedAt,
                )

                // 3. Persist a POST_CALL note if the agent typed anything.
                if (state.noteText.isNotBlank()) {
                    val note = Note(
                        mobileSyncId = UUID.randomUUID().toString(),
                        clientId = clientId,
                        interactionMobileSyncId = interaction.mobileSyncId,
                        content = state.noteText.trim(),
                        type = NoteType.POST_CALL,
                        deviceCreatedAt = Instant.now(),
                        syncStatus = SyncStatus.PENDING,
                    )
                    noteRepository.save(note)
                    clientRepository.updateLastNoteLocally(clientId, state.noteText.trim())
                }

                // 4. Schedule a follow-up if Interested.
                if (outcome == CallOutcome.INTERESTED) {
                    val date = state.followUpDate ?: error("date required")
                    val time = state.followUpTime ?: error("time required")
                    val scheduledAt = date.atTime(time).atZone(zone).toInstant()
                    // The optional Note above already covers the "agent
                    // wants to write context" case — follow-up reason was
                    // redundant. We send an empty string so the existing
                    // backend contract (reason: string, non-optional)
                    // keeps working without a deploy.
                    val followUp = FollowUp(
                        mobileSyncId = UUID.randomUUID().toString(),
                        clientId = clientId,
                        clientName = state.client?.name,
                        clientPhone = state.client?.phone,
                        interactionMobileSyncId = interaction.mobileSyncId,
                        scheduledAt = scheduledAt,
                        reason = "",
                        status = FollowUpStatus.PENDING,
                        completedAt = null,
                        deviceCreatedAt = Instant.now(),
                        syncStatus = SyncStatus.PENDING,
                        completionSyncStatus = SyncStatus.SYNCED,
                    )
                    followUpRepository.save(followUp)
                }
            }
                .onSuccess {
                    syncScheduler.triggerImmediateSync()
                    // Hand off to the orchestrator if a session is active —
                    // it advances the cursor and tells us where to go next.
                    val target = autoCallOrchestrator.onPostCallSaved(outcome)
                    val event = when (target) {
                        is AutoCallNavTarget.NextClient ->
                            PostCallEvent.SavedNextInSession(target.clientId)
                        AutoCallNavTarget.SessionSummary ->
                            PostCallEvent.SavedSessionComplete
                        null -> PostCallEvent.Saved
                    }
                    _events.send(event)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.message ?: "Could not save",
                        )
                    }
                }
        }
    }

    fun exitAutoCall() {
        autoCallOrchestrator.exit()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val interaction = runCatching {
                interactionRepository.findById(interactionId)
            }.getOrNull()
            val client = runCatching {
                clientRepository.findById(clientId)
            }.getOrNull()

            if (interaction == null || client == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Couldn't load call details",
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    client = client,
                    interaction = interaction,
                    // Prefilled outcome wins over the placeholder stored in the
                    // interaction itself.
                    selectedOutcome = prefilledOutcome ?: interaction.outcome,
                )
            }
        }
    }
}
