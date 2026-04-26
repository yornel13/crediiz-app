package com.project.vortex.callsagent.presentation.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.data.sync.SyncManager
import com.project.vortex.callsagent.data.sync.SyncResult
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.ClientDismissal
import com.project.vortex.callsagent.domain.model.MissedCall
import com.project.vortex.callsagent.domain.repository.ClientDismissalRepository
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
    private val clientDismissalRepository: ClientDismissalRepository,
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

    /**
     * Pendientes list: PENDING clients, optionally search-filtered.
     * Drives the Pendientes view.
     */
    val pendingClients: StateFlow<List<Client>> = _searchQuery
        .flatMapLatest { query ->
            val q = query.trim()
            if (q.isBlank()) clientRepository.observeAssigned(ClientStatus.PENDING)
            else clientRepository.searchAssigned(ClientStatus.PENDING, q)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Recientes feed — unified list of recent calls AND recent
     * dismissals, deduped by clientId (the most recent intent wins).
     * Sorted by timestamp desc.
     */
    val recentEntries: StateFlow<List<RecentEntry>> = combine(
        clientRepository.observeRecent(recentSince),
        clientDismissalRepository.observeActiveSince(recentSince),
        _searchQuery,
    ) { calledClients, dismissals, query ->
        buildRecentEntries(calledClients, dismissals, query.trim())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Search-independent count for the pill. Recientes count = unique
     * client IDs across calls + dismissals.
     */
    val totalRecentCount: StateFlow<Int> = combine(
        clientRepository.observeRecent(recentSince),
        clientDismissalRepository.observeActiveSince(recentSince),
    ) { calledClients, dismissals ->
        val ids = calledClients.map { it.id }.toMutableSet()
        dismissals.forEach { ids += it.clientId }
        ids.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    private suspend fun buildRecentEntries(
        calledClients: List<Client>,
        dismissals: List<ClientDismissal>,
        query: String,
    ): List<RecentEntry> {
        val byClientId = mutableMapOf<String, RecentEntry>()
        // Start with calls; dismissals will override when more recent.
        for (client in calledClients) {
            client.lastCalledAt?.let {
                byClientId[client.id] = RecentEntry.Called(client, it)
            }
        }
        for (dismissal in dismissals) {
            // Find the client snapshot. Prefer the one in `calledClients`
            // (already loaded). Fallback: hit the repo. Without a client
            // we can't render — skip.
            val existing = byClientId[dismissal.clientId]
            val client = (existing?.client) ?: clientRepository.findById(dismissal.clientId)
            if (client == null) continue

            val entry = RecentEntry.Dismissed(
                client = client,
                timestamp = dismissal.dismissedAt,
                dismissal = dismissal,
            )
            // Dismissal wins if it's more recent than an existing call entry.
            val existingTs = existing?.timestamp
            if (existingTs == null || dismissal.dismissedAt.isAfter(existingTs)) {
                byClientId[dismissal.clientId] = entry
            }
        }

        var entries = byClientId.values.toList()
        if (query.isNotBlank()) {
            val needle = query.lowercase()
            entries = entries.filter {
                it.client.name.lowercase().contains(needle) ||
                    it.client.phone.contains(needle)
            }
        }
        return entries.sortedByDescending { it.timestamp }
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
            // Both Pendientes and Recientes are backed by the local
            // PENDING set plus rows that left PENDING via post-call
            // updates (which stay in the local DB). Pulling PENDING
            // covers both views.
            clientRepository.refreshAssigned(ClientStatus.PENDING)
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
        // Auto-call always operates on the Pendientes queue regardless
        // of which view is active when the FAB is tapped.
        val ids = pendingClients.value.map { it.id }
        return autoCallOrchestrator.startSession(
            clientIds = ids,
            sourceTab = HomeTabs.CLIENTS,
        )
    }

    // ─── Dismissal actions ─────────────────────────────────────────────────

    fun dismissClient(
        clientId: String,
        reasonCode: DismissalReasonCode?,
        freeFormReason: String?,
    ) {
        viewModelScope.launch {
            clientDismissalRepository.dismiss(clientId, reasonCode, freeFormReason)
        }
    }

    fun undoDismissal(clientId: String) {
        viewModelScope.launch {
            clientDismissalRepository.undo(clientId)
        }
    }
}
