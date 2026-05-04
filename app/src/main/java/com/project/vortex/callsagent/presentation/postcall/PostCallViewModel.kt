package com.project.vortex.callsagent.presentation.postcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.domain.call.CallEndingInsight
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
    /**
     * True when the screen was opened via Phase 7.5 recovery (the
     * call ended more than ~5 min ago without the agent saving). UI
     * shows a "Recuperando llamada anterior" banner so the agent
     * understands why they landed here on app open.
     */
    val isRecovering: Boolean = false,

    /**
     * Subset of [CallOutcome] values the screen will render as
     * buttons. When empty, all five outcomes are shown — that's the
     * fallback for orphan recovery where we don't know what happened
     * during the call. Populated by the SIP engine via the navigation
     * route (see `EndedCall.allowedOutcomes`).
     */
    val allowedOutcomes: List<CallOutcome> = emptyList(),

    /**
     * Optional Spanish phrase shown under the "Call outcome" header
     * explaining why options were filtered (e.g. "Línea ocupada.").
     * Null on the orphan path or when the call answered.
     */
    val reasonLabel: String? = null,
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

    /**
     * Comma-separated list of CallOutcome names from the nav route
     * (e.g. "NO_ANSWER,BUSY"). Empty / null → fallback to all five.
     */
    private val allowedOutcomes: List<CallOutcome> =
        savedStateHandle.get<String>("allowedOutcomes")
            ?.split(',')
            ?.mapNotNull { runCatching { CallOutcome.valueOf(it.trim()) }.getOrNull() }
            ?: emptyList()

    private val reasonLabel: String? =
        savedStateHandle.get<String>("reasonLabel")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(
        PostCallUiState(
            allowedOutcomes = allowedOutcomes,
            reasonLabel = reasonLabel,
        ),
    )
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
                // Mark this interaction as confirmed by the agent — the
                // orphan-recovery flow on next app start will skip it.
                interactionRepository.markConfirmed(interaction.mobileSyncId)

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

            // If the call ended more than 5 minutes ago and we're
            // landing on Post-Call, treat it as a recovery flow.
            val ageMinutes = java.time.Duration
                .between(interaction.callEndedAt, Instant.now())
                .toMinutes()
            val isRecovering = ageMinutes >= RECOVERY_AGE_MINUTES

            // On the orphan-recovery path the navigation route does not
            // carry the SIP-engine insight. Reconstruct it from the
            // `disconnectCause` we persisted on the row so the screen
            // filters outcomes the same way as the fresh-call path.
            val recoveredInsight = if (allowedOutcomes.isEmpty() && reasonLabel == null) {
                CallEndingInsight.fromPersistedCause(interaction.disconnectCause)
            } else null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    client = client,
                    interaction = interaction,
                    // Prefilled outcome wins over the placeholder stored in the
                    // interaction itself, which in turn wins over a recovered
                    // insight.
                    selectedOutcome = prefilledOutcome
                        ?: recoveredInsight?.suggestedOutcome
                        ?: interaction.outcome,
                    isRecovering = isRecovering,
                    allowedOutcomes = it.allowedOutcomes
                        .takeIf { existing -> existing.isNotEmpty() }
                        ?: recoveredInsight?.allowedOutcomes.orEmpty(),
                    reasonLabel = it.reasonLabel ?: recoveredInsight?.reasonLabel,
                )
            }
        }
    }

    private companion object {
        const val RECOVERY_AGE_MINUTES = 5L
    }
}
