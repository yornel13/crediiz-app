package com.project.vortex.callsagent.presentation.precall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import kotlinx.coroutines.flow.firstOrNull
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.call.CallController
import com.project.vortex.callsagent.domain.call.CallReadiness
import com.project.vortex.callsagent.domain.call.CallReadinessProvider
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.model.ActivityEvent
import com.project.vortex.callsagent.domain.repository.ClientDismissalRepository
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import com.project.vortex.callsagent.presentation.autocall.AutoCallNavTarget
import com.project.vortex.callsagent.presentation.autocall.AutoCallOrchestrator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class PreCallUiState(
    val isLoading: Boolean = true,
    val client: Client? = null,
    val errorMessage: String? = null,
    val isSubmittingNote: Boolean = false,
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

/**
 * Assisted-inject so this ViewModel can be reused in two places:
 *  - [PreCallScreen] full-screen, where the clientId comes from nav args.
 *  - The adaptive detail pane, where the clientId comes from the
 *    `ListDetailPaneScaffoldNavigator`'s `contentKey`.
 *
 * In both cases the caller provides the clientId explicitly via
 * [Factory.create]; we no longer reach for SavedStateHandle.
 */
@HiltViewModel(assistedFactory = PreCallViewModel.Factory::class)
class PreCallViewModel @AssistedInject constructor(
    @Assisted private val clientIdArg: String,
    private val clientRepository: ClientRepository,
    private val clientDismissalRepository: ClientDismissalRepository,
    private val noteRepository: NoteRepository,
    private val followUpRepository: FollowUpRepository,
    private val interactionRepository: InteractionRepository,
    private val syncScheduler: SyncScheduler,
    private val callController: CallController,
    private val autoCallOrchestrator: AutoCallOrchestrator,
    settingsPreferences: SettingsPreferences,
    private val callReadinessProvider: CallReadinessProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(clientId: String): PreCallViewModel
    }

    /**
     * VoIP + SIP readiness — drives the persistent banner above the
     * hero and disables the "Llamar" CTA when not [CallReadiness.Ready].
     */
    val callReadiness: StateFlow<CallReadiness> = callReadinessProvider.readiness

    /** Tap "Reintentar" on the SIP-disconnected banner. */
    fun retrySipRegistration() = callReadinessProvider.retry()

    /**
     * Promote/demote the thermometer of the currently displayed
     * INTERESTED client. No-op if the client isn't INTERESTED
     * (button is hidden in that case but a stale tap could race).
     */
    fun updateInterestLevel(level: com.project.vortex.callsagent.common.enums.InterestLevel) {
        val client = _uiState.value.client ?: return
        if (client.status != com.project.vortex.callsagent.common.enums.ClientStatus.INTERESTED) return
        viewModelScope.launch {
            val result = clientRepository.updateInterestLevel(
                clientId = client.id,
                level = level,
                previous = client.interestLevel,
            )
            when (result) {
                is com.project.vortex.callsagent.domain.result.OperationResult.Success ->
                    _snackbar.send(
                        com.project.vortex.callsagent.presentation.common.SnackbarMessage(
                            textRes = R.string.precall_snack_interest_updated,
                            tone = com.project.vortex.callsagent.presentation.common.SnackbarMessage.Tone.SUCCESS,
                        ),
                    )
                is com.project.vortex.callsagent.domain.result.OperationResult.Failure ->
                    _snackbar.send(snackbarFor(result.error))
            }
        }
    }

    /**
     * Out-of-band status change (HOW_IT_WORKS §7). The agent moves the
     * current client to [toStatus] without placing a call. Optimistic
     * via [ClientRepository.agentStatusChange] (rollback on network
     * failure). Every result emits a snackbar — success or failure.
     */
    fun agentStatusChange(
        toStatus: com.project.vortex.callsagent.common.enums.ClientStatus,
        reason: String?,
        level: com.project.vortex.callsagent.common.enums.InterestLevel?,
    ) {
        val client = _uiState.value.client ?: return
        viewModelScope.launch {
            val result = clientRepository.agentStatusChange(
                clientId = client.id,
                toStatus = toStatus,
                previousStatus = client.status,
                previousLevel = client.interestLevel,
                reason = reason,
                level = level,
            )
            when (result) {
                is com.project.vortex.callsagent.domain.result.OperationResult.Success ->
                    _snackbar.send(
                        com.project.vortex.callsagent.presentation.common.SnackbarMessage(
                            textRes = R.string.precall_snack_client_updated,
                            args = listOf(client.name),
                            tone = com.project.vortex.callsagent.presentation.common.SnackbarMessage.Tone.SUCCESS,
                        ),
                    )
                is com.project.vortex.callsagent.domain.result.OperationResult.Failure ->
                    _snackbar.send(snackbarFor(result.error))
            }
        }
    }

    /**
     * Agent-driven scheduling **without making a call** (HOW_IT_WORKS
     * §7 extension). If the client isn't INTERESTED yet, promote them
     * with the chosen [level] first (agent-status-change). If a pending
     * follow-up already exists for the client, mark it completed
     * before creating the new one (replace semantics — D3 from the
     * design discussion).
     *
     * All steps are sequenced inside one coroutine: any failure short-
     * circuits and emits a snackbar.
     */
    fun scheduleFollowUp(
        scheduledAt: java.time.Instant,
        reason: String?,
        level: com.project.vortex.callsagent.common.enums.InterestLevel,
        replacePending: Boolean,
    ) {
        val client = _uiState.value.client ?: return
        viewModelScope.launch {
            // 1. Promote to INTERESTED if needed. agent-status-change
            //    needs no reason for INTERESTED targets; we default
            //    one for the audit trail so the admin sees context.
            if (client.status != com.project.vortex.callsagent.common.enums.ClientStatus.INTERESTED) {
                val promotion = clientRepository.agentStatusChange(
                    clientId = client.id,
                    toStatus = com.project.vortex.callsagent.common.enums.ClientStatus.INTERESTED,
                    previousStatus = client.status,
                    previousLevel = client.interestLevel,
                    reason = reason ?: "Agendado para seguimiento",
                    level = level,
                )
                if (promotion is com.project.vortex.callsagent.domain.result.OperationResult.Failure) {
                    _snackbar.send(snackbarFor(promotion.error))
                    return@launch
                }
            } else if (level != client.interestLevel) {
                // Same-status promotion — only PATCH the thermometer.
                val levelResult = clientRepository.updateInterestLevel(
                    clientId = client.id,
                    level = level,
                    previous = client.interestLevel,
                )
                if (levelResult is com.project.vortex.callsagent.domain.result.OperationResult.Failure) {
                    _snackbar.send(snackbarFor(levelResult.error))
                    return@launch
                }
            }

            // 2. Replace semantics — mark the existing follow-up
            //    completed locally. The sync push will propagate this
            //    to the backend (the only supported "remove from
            //    agenda" path; see HOW_IT_WORKS handoff §D3).
            if (replacePending) {
                val now = java.time.Instant.now()
                followUpRepository.observeNextPendingForClient(client.id, now)
                    .firstOrNull()
                    ?.let { existing ->
                        followUpRepository.markCompletedLocally(existing.mobileSyncId, now)
                    }
            }

            // 3. Persist the new follow-up locally (no interaction
            //    attached — `interactionMobileSyncId = null`). Outbox
            //    syncs it on the next push.
            val followUp = com.project.vortex.callsagent.domain.model.FollowUp(
                mobileSyncId = java.util.UUID.randomUUID().toString(),
                clientId = client.id,
                clientName = client.name,
                clientPhone = client.phone,
                interactionMobileSyncId = null,
                scheduledAt = scheduledAt,
                reason = reason ?: "",
                status = com.project.vortex.callsagent.common.enums.FollowUpStatus.PENDING,
                completedAt = null,
                deviceCreatedAt = java.time.Instant.now(),
                syncStatus = com.project.vortex.callsagent.common.enums.SyncStatus.PENDING,
                completionSyncStatus = com.project.vortex.callsagent.common.enums.SyncStatus.SYNCED,
            )
            followUpRepository.save(followUp)
            syncScheduler.triggerImmediateSync()

            _snackbar.send(
                com.project.vortex.callsagent.presentation.common.SnackbarMessage(
                    textRes = R.string.precall_snack_followup_scheduled,
                    tone = com.project.vortex.callsagent.presentation.common.SnackbarMessage.Tone.SUCCESS,
                ),
            )
        }
    }

    /**
     * Maps a typed client error to a localisable snackbar payload. The
     * @StringRes is resolved by the screen against the Activity's
     * locale-overridden Context — this VM carries no resolved strings.
     */
    private fun snackbarFor(
        error: com.project.vortex.callsagent.domain.error.ClientError,
    ): com.project.vortex.callsagent.presentation.common.SnackbarMessage {
        val textRes = when (error) {
            is com.project.vortex.callsagent.domain.error.ClientError.ReasonRequired ->
                R.string.precall_err_reason_required
            com.project.vortex.callsagent.domain.error.ClientError.NotAssigned ->
                R.string.precall_err_not_assigned
            is com.project.vortex.callsagent.domain.error.ClientError.TargetNotAllowed ->
                R.string.precall_err_target_not_allowed
            com.project.vortex.callsagent.domain.error.ClientError.InterestLevelNotApplicable ->
                R.string.precall_err_interest_not_applicable
            com.project.vortex.callsagent.domain.error.ClientError.NotFound ->
                R.string.precall_err_not_found
            com.project.vortex.callsagent.domain.error.ClientError.Conflict ->
                R.string.precall_err_conflict
            com.project.vortex.callsagent.domain.error.ClientError.SessionExpired ->
                R.string.precall_err_session_expired
            com.project.vortex.callsagent.domain.error.ClientError.Network ->
                R.string.precall_err_network
            is com.project.vortex.callsagent.domain.error.ClientError.Unknown ->
                R.string.precall_err_unknown
        }
        // Only Unknown interpolates a backend-supplied detail string.
        val args: List<Any> = when (error) {
            is com.project.vortex.callsagent.domain.error.ClientError.Unknown ->
                listOf(error.detail)
            else -> emptyList()
        }
        val tone = when (error) {
            com.project.vortex.callsagent.domain.error.ClientError.Network,
            com.project.vortex.callsagent.domain.error.ClientError.Conflict ->
                com.project.vortex.callsagent.presentation.common.SnackbarMessage.Tone.WARN
            else ->
                com.project.vortex.callsagent.presentation.common.SnackbarMessage.Tone.ERROR
        }
        return com.project.vortex.callsagent.presentation.common.SnackbarMessage(
            textRes = textRes,
            args = args,
            tone = tone,
        )
    }

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
        // Hard gate: refuse to dial when SIP is not ready (no VoIP
        // account, or registered failed, or still connecting). The
        // CTA is already disabled in the UI but we re-check here so
        // a stale recomposition can't fire a SIP INVITE that would
        // loop on REGISTER timeouts.
        if (callReadiness.value !is CallReadiness.Ready) return
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
            when (val target = autoCallOrchestrator.skipCurrent(skippedClientId = clientId)) {
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

    private val clientId: String = clientIdArg

    private val _uiState = MutableStateFlow(PreCallUiState())
    val uiState: StateFlow<PreCallUiState> = _uiState.asStateFlow()

    private val _events = Channel<PreCallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Channel of snackbar payloads triggered by client-write
     * operations (interest level, agent-status-change). The screen
     * collects these and forwards to its `SnackbarHostState`.
     *
     * Project invariant: every typed [ClientWriteError] MUST produce
     * exactly one [SnackbarMessage] here. Silent failure is a bug.
     */
    private val _snackbar = Channel<com.project.vortex.callsagent.presentation.common.SnackbarMessage>(
        Channel.BUFFERED,
    )
    val snackbarMessages = _snackbar.receiveAsFlow()

    val notes: StateFlow<List<Note>> = noteRepository.observeByClient(clientId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Agent preference: "Mostrar historial completo" toggle in
     * Settings. When false, the PreCall timeline filters out
     * non-Note events. Sourced from [SettingsPreferences] so the
     * choice persists across clients and app launches without per-
     * screen toggles.
     */
    val showFullActivityHistory: StateFlow<Boolean> =
        settingsPreferences.showFullActivityHistoryFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
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

    /**
     * Unified per-client activity timeline, sorted newest-first.
     * Combines local note and call-interaction flows plus a synthetic
     * "lead imported" anchor derived from the client record. The
     * timeline UI renders this list as a single sequence — see
     * [ActivityEvent].
     *
     * Combination happens here (in the VM) rather than in a dedicated
     * repository because the join is purely view-shaped: it picks
     * specific fields out of two unrelated tables for display only.
     * If a third source (status changes, dismissals) lands, extract
     * the combine into `ActivityRepository`.
     */
    val activity: StateFlow<List<ActivityEvent>> =
        kotlinx.coroutines.flow.combine(
            noteRepository.observeByClient(clientId),
            interactionRepository.observeByClient(clientId),
            // `assignedAt` from the currently-loaded client. Projected
            // out of `_uiState` so the timeline re-emits when the
            // client row reloads (e.g. after a reassign). Distinct so
            // unrelated `_uiState` mutations (sheets opening, etc.)
            // don't churn the combine.
            _uiState.map { it.client?.assignedAt }.distinctUntilChanged(),
        ) { notes, calls, assignedAt ->
            val noteEvents = notes.map { note ->
                ActivityEvent.NoteEntry(
                    occurredAt = note.deviceCreatedAt,
                    agentId = null,
                    content = note.content,
                    type = note.type,
                )
            }
            val callEvents = calls.map { call ->
                ActivityEvent.Call(
                    occurredAt = call.callStartedAt,
                    agentId = null,
                    durationSeconds = call.durationSeconds,
                    outcome = call.outcome,
                )
            }
            // Assignment anchor — only when the backend gave us an
            // actual `assignedAt`. Legacy rows with null stay
            // invisible (better than fabricating a date the agent
            // would read as truth).
            val assignedEvents = assignedAt
                ?.let { listOf(ActivityEvent.AssignedToAgent(occurredAt = it)) }
                .orEmpty()

            // No synthetic LeadImported anchor (it counted but was
            // filtered out of the empty-state check, producing a
            // "1 REGISTRO" / empty-timeline mismatch). The
            // AssignedToAgent anchor above replaces it where data
            // exists — and counts as a real registro.
            (noteEvents + callEvents + assignedEvents)
                .sortedByDescending { it.occurredAt }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        loadClient()
    }

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
