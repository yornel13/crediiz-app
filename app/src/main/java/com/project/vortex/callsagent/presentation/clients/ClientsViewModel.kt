package com.project.vortex.callsagent.presentation.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.sync.SyncManager
import com.project.vortex.callsagent.data.sync.SyncResult
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.MissedCall
import com.project.vortex.callsagent.domain.model.Note
import java.util.UUID
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.MissedCallRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import com.project.vortex.callsagent.presentation.autocall.AutoCallOrchestrator
import com.project.vortex.callsagent.presentation.navigation.HomeTabs
import kotlinx.coroutines.flow.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ClientsUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val missedCallRepository: MissedCallRepository,
    private val interactionRepository: InteractionRepository,
    private val noteRepository: NoteRepository,
    private val followUpRepository: FollowUpRepository,
    private val autoCallOrchestrator: AutoCallOrchestrator,
    private val syncScheduler: SyncScheduler,
    syncManager: SyncManager,
) : ViewModel() {

    // ─── Sync indicator (Phase 4.5.3 / UX-4) ────────────────────────────────

    /**
     * Aggregate pending-sync count across all locally-created entities.
     * Drives the sync indicator chip on the Clients hero.
     */
    private val pendingCount: kotlinx.coroutines.flow.Flow<Int> = combine(
        interactionRepository.observePendingCount(),
        noteRepository.observePendingCount(),
        followUpRepository.observePendingCount(),
    ) { i, n, f -> i + n + f }

    val syncIndicator: StateFlow<SyncIndicatorState> = combine(
        pendingCount,
        syncScheduler.observeIsSyncing(),
        syncManager.lastResult,
    ) { count, isSyncing, lastResult ->
        when {
            isSyncing -> SyncIndicatorState.Syncing
            lastResult is SyncResult.Error && count > 0 ->
                SyncIndicatorState.Failed(pendingCount = count)
            count > 0 -> SyncIndicatorState.Pending(count = count)
            else -> SyncIndicatorState.AllSynced
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncIndicatorState.AllSynced,
    )

    fun forceSyncNow() {
        syncScheduler.triggerImmediateSync()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewKind = MutableStateFlow(ClientsViewKind.PENDIENTES)
    val viewKind: StateFlow<ClientsViewKind> = _viewKind.asStateFlow()

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

    /**
     * Recientes window. Captured once per ViewModel lifetime — re-entering
     * the screen recomputes. A 24 h cutoff is forgiving enough that
     * staleness within a session is irrelevant.
     */
    private val recentSince: Instant = Instant.now().minus(24, ChronoUnit.HOURS)

    // ─── Per-view counters (search-independent so the pills stay stable) ────

    /**
     * Total count of PENDING assigned clients. Independent of the search
     * query so the hero counter stays stable while the agent types.
     */
    val totalPendingCount: StateFlow<Int> =
        clientRepository.observeAssigned(ClientStatus.PENDING)
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    val totalRecentCount: StateFlow<Int> =
        clientRepository.observeRecent(recentSince)
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    val totalInterestedCount: StateFlow<Int> =
        clientRepository.observeAssigned(ClientStatus.INTERESTED)
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /**
     * Observes clients for the **active view**, switching between
     * unfiltered and search-filtered Flows whenever the query changes.
     * Room Flows emit on every underlying change.
     */
    val clients: StateFlow<List<Client>> = combine(_viewKind, _searchQuery) { kind, q -> kind to q }
        .flatMapLatest { (kind, query) -> sourceFor(kind, query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private fun sourceFor(kind: ClientsViewKind, query: String): Flow<List<Client>> {
        val trimmed = query.trim()
        val isSearch = trimmed.isNotBlank()
        return when (kind) {
            ClientsViewKind.PENDIENTES ->
                if (isSearch) clientRepository.searchAssigned(ClientStatus.PENDING, trimmed)
                else clientRepository.observeAssigned(ClientStatus.PENDING)
            ClientsViewKind.RECIENTES ->
                if (isSearch) clientRepository.searchRecent(recentSince, trimmed)
                else clientRepository.observeRecent(recentSince)
            ClientsViewKind.INTERESADOS ->
                if (isSearch) clientRepository.searchAssigned(ClientStatus.INTERESTED, trimmed)
                else clientRepository.observeAssigned(ClientStatus.INTERESTED)
        }
    }

    init {
        refresh()
    }

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    /**
     * Switch which top-level view is displayed. Search query is preserved
     * across switches by design — agents often look up "Maria" across
     * Pendientes / Recientes / Interesados.
     */
    fun onViewKindChange(kind: ClientsViewKind) {
        _viewKind.value = kind
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            // Refresh the status that backs the active view. Recientes
            // is fed by client rows already kept in sync by the post-call
            // path; pulling PENDING covers the most common case.
            val statusToPull = when (_viewKind.value) {
                ClientsViewKind.PENDIENTES, ClientsViewKind.RECIENTES -> ClientStatus.PENDING
                ClientsViewKind.INTERESADOS -> ClientStatus.INTERESTED
            }
            clientRepository.refreshAssigned(statusToPull)
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.message ?: "Failed to refresh clients",
                    )
                }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    /** Trigger an explicit sync (covers any pending records the user might have offline). */
    fun forceSync() {
        syncScheduler.triggerImmediateSync()
    }

    /**
     * Save a manual note for a client without going through the call flow.
     * Used by the "Add note" sheet on the Recientes / Interesados cards.
     * Late-recall after a call, lead-management journaling, etc.
     */
    fun addManualNote(clientId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val note = Note(
                mobileSyncId = UUID.randomUUID().toString(),
                clientId = clientId,
                interactionMobileSyncId = null,
                content = trimmed,
                type = NoteType.MANUAL,
                deviceCreatedAt = Instant.now(),
                syncStatus = SyncStatus.PENDING,
            )
            noteRepository.save(note)
            clientRepository.updateLastNoteLocally(clientId, trimmed)
            syncScheduler.triggerImmediateSync()
        }
    }

    // ─── Missed calls (Phase 3.5) ───────────────────────────────────────────

    val missedCalls: StateFlow<List<MissedCall>> =
        missedCallRepository.observeUnacknowledged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun acknowledgeMissedCall(id: String) {
        viewModelScope.launch { missedCallRepository.markAcknowledged(id) }
    }

    fun acknowledgeAllMissedCalls() {
        viewModelScope.launch { missedCallRepository.markAllAcknowledged() }
    }

    /**
     * Start an auto-call session over the currently observed PENDING
     * queue (in `queueOrder`). Returns the first client's id so the
     * caller can navigate to PreCall, or null if the queue is empty.
     */
    fun startAutoCall(): String? {
        val ids = clients.value.map { it.id }
        return autoCallOrchestrator.startSession(
            clientIds = ids,
            sourceTab = HomeTabs.CLIENTS,
        )
    }
}
