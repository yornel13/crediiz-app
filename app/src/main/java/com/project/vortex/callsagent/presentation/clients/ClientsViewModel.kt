package com.project.vortex.callsagent.presentation.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.data.sync.ConnectivityObserver
import com.project.vortex.callsagent.data.sync.SyncManager
import com.project.vortex.callsagent.data.sync.SyncResult
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.AgentStatusChangeLocal
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.MissedCall
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.MissedCallRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import com.project.vortex.callsagent.presentation.autocall.AutoCallOrchestrator
import com.project.vortex.callsagent.presentation.navigation.HomeTabs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val connectivityObserver: ConnectivityObserver,
    syncManager: SyncManager,
) : ViewModel() {

    /**
     * The currently active auto-call session, or null when no batch is
     * running. Exposed for the split-mode scaffold to react to
     * orchestrator advances after PostCall save: when the session's
     * `currentIndex` moves to a new client, the scaffold re-points its
     * detail pane in place instead of letting the full-screen PreCall
     * route take over.
     */
    val autoCallSession = autoCallOrchestrator.session

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

    /**
     * One-shot local lookup used by the adaptive detail pane on
     * tablets. Hits Room directly via [ClientRepository.findById] —
     * fast (<5ms typical) and covers clients that may have already
     * fallen off the pending/recent flows (status changed, dismissed).
     *
     * Suspend (not Flow) on purpose: the detail pane fetches once per
     * selection and re-fetches on id change via [produceState]. We
     * don't need a long-lived subscription for a single record.
     */
    suspend fun findClientLocally(id: String): Client? =
        clientRepository.findById(id)

    /**
     * Live note feed for the adaptive detail pane. Returns a hot Flow
     * scoped to the caller composable's collection — Room emits on any
     * note INSERT/UPDATE/DELETE for the client, so the timeline in the
     * pane stays in sync with PreCall when the agent is mid-call.
     */
    fun observeNotesForClient(clientId: String) =
        noteRepository.observeByClient(clientId)

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
     * "Sin llamar" sub-feed — never-called PENDING clients, ordered
     * by `queueOrder` ASC. Search-aware: when the user types in the
     * search bar, this flow returns the flat filtered list (no
     * grouping). When the query is empty, the UI consumes
     * [pendingNeverCalledByDate] instead so the date headers appear.
     *
     * Both flows must stay in sync — keep them sourced from the same
     * repo methods.
     */
    val pendingNeverCalled: StateFlow<List<Client>> = _searchQuery
        .flatMapLatest { query ->
            val q = query.trim()
            if (q.isBlank()) clientRepository.observePendingNeverCalled()
            else clientRepository.searchPendingNeverCalled(q)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Same data as [pendingNeverCalled] but grouped by `assignedAt`
     * date into [PendingDateBucket]s ordered oldest-first. Drives the
     * date headers in the Pendientes → "Sin llamar" view.
     *
     * Only populated when the search query is **blank** — searching
     * by name/phone produces a flat list (the agent is already
     * navigating by identity, headers add noise).
     */
    val pendingNeverCalledByDate: StateFlow<Map<PendingDateBucket, List<Client>>> =
        _searchQuery
            .flatMapLatest { query ->
                if (query.isNotBlank()) flowOf(emptyMap())
                else clientRepository.observePendingNeverCalled().map {
                    groupPendingNeverCalledByAssignedDate(it)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    /**
     * "Para reintentar" sub-feed — PENDING clients with at least one
     * past call attempt (NO_ANSWER / BUSY). Sorted oldest-call-first
     * so the most ready-to-retry leads bubble up. Search-aware.
     */
    val pendingForRetry: StateFlow<List<Client>> = _searchQuery
        .flatMapLatest { query ->
            val q = query.trim()
            if (q.isBlank()) clientRepository.observePendingForRetry()
            else clientRepository.searchPendingForRetry(q)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Total of the whole visible Pendientes list — "Sin llamar" + "Para
     * reintentar". Backs the hero headline ("X clientes") and the "X of Y"
     * search summary, both of which span both sub-lists. Search-independent
     * so the count stays stable while typing.
     */
    val totalPendingCount: StateFlow<Int> = combine(
        clientRepository.observePendingNeverCalled(),
        clientRepository.observePendingForRetry(),
    ) { neverCalled, forRetry -> neverCalled.size + forRetry.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /**
     * "Pendientes" pill counter — clients THIS agent still has to call:
     * PENDING with no personal attempt yet ("Sin llamar"). Deliberately
     * EXCLUDES "Para reintentar" (already contacted at least once), which are
     * no longer "pending to call". Search-independent so the count stays
     * stable while typing.
     */
    val pendingToCallCount: StateFlow<Int> =
        clientRepository.observePendingNeverCalled()
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /**
     * Recientes feed — unified list of:
     *  - recent calls (`lastCalledAt` in window),
     *  - recent agent-driven status changes (no call, removals included).
     *
     * Deduped by clientId — the most recent agent intent wins.
     * Sorted by timestamp desc.
     */
    val recentEntries: StateFlow<List<RecentEntry>> = combine(
        clientRepository.observeRecent(recentSince),
        clientRepository.observeRecentAgentStatusChanges(recentSince),
        _searchQuery,
    ) { calledClients, statusChanges, query ->
        buildRecentEntries(calledClients, statusChanges, query.trim())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Search-independent count for the pill. Recientes count = unique
     * client IDs across calls + status changes.
     */
    val totalRecentCount: StateFlow<Int> = combine(
        clientRepository.observeRecent(recentSince),
        clientRepository.observeRecentAgentStatusChanges(recentSince),
    ) { calledClients, statusChanges ->
        val ids = calledClients.map { it.id }.toMutableSet()
        statusChanges.forEach { ids += it.clientId }
        ids.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    private suspend fun buildRecentEntries(
        calledClients: List<Client>,
        statusChanges: List<AgentStatusChangeLocal>,
        query: String,
    ): List<RecentEntry> {
        val byClientId = mutableMapOf<String, RecentEntry>()
        // 1. Calls first — base layer.
        for (client in calledClients) {
            client.lastCalledAt?.let {
                byClientId[client.id] = RecentEntry.Called(client, it)
            }
        }
        // 2. Status changes (removals included) — override calls when
        //    more recent. Clients moved without a call may not be in
        //    `calledClients` so we fetch them on miss.
        for (change in statusChanges) {
            val existing = byClientId[change.clientId]
            val client = (existing?.client) ?: clientRepository.findById(change.clientId)
            if (client == null) continue
            val existingTs = existing?.timestamp
            if (existingTs == null || change.timestamp.isAfter(existingTs)) {
                byClientId[change.clientId] = RecentEntry.StatusChanged(
                    client = client,
                    timestamp = change.timestamp,
                    change = change,
                )
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
        observeConnectivityForErrorClear()
    }

    /**
     * Watch connectivity transitions: as soon as the device goes from
     * offline to online, drop any stale "Unable to resolve host…" /
     * network-error message and re-fetch. The SyncScheduler also
     * triggers on reconnect, so the data half is covered; this just
     * clears the banner the user is staring at.
     */
    private fun observeConnectivityForErrorClear() {
        viewModelScope.launch {
            connectivityObserver.isOnline
                .drop(1) // skip the initial StateFlow value
                .filter { it } // only on offline → online transitions
                .collect {
                    _uiState.value = _uiState.value.copy(errorMessage = null)
                    refresh()
                }
        }
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
            // One pull mirrors the whole assigned set (any status): covers
            // Pendientes, Interesados/Agenda and Recientes (which now keeps
            // terminal-but-assigned clients) in a single round-trip.
            clientRepository.refreshAssigned()
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
     * queue. Returns the first client's id so the caller can navigate
     * to PreCall, or null if the queue is empty.
     *
     * **Order contract — must match the UI exactly:**
     *  1. "Sin llamar" rendered as date-grouped sections (oldest
     *     bucket first, newest last). When the agent isn't searching,
     *     [pendingNeverCalledByDate] is the source of truth: its
     *     LinkedHashMap preserves bucket order, and clients inside
     *     each bucket keep DAO `queueOrder ASC`.
     *  2. "Para reintentar" follows, ordered by `lastCalledAt ASC`
     *     (same flat list the UI uses).
     *
     * If the queue order diverged from the UI, the orchestrator
     * would think the agent's first-tap client was at the END of
     * the queue and immediately complete the session (the
     * "1/53 then SessionSummary" bug — see logcat from 2026-05-23).
     */
    fun startAutoCall(): String? {
        // Prefer the grouped flow when available (= no active search).
        // Falls back to the flat list when the agent is searching —
        // in that mode the UI also renders flat (see ClientsScreen).
        val neverCalledIds: List<String> =
            pendingNeverCalledByDate.value
                .takeIf { it.isNotEmpty() }
                ?.values
                ?.flatten()
                ?.map { it.id }
                ?: pendingNeverCalled.value.map { it.id }
        val retryIds = pendingForRetry.value.map { it.id }
        val ids = neverCalledIds + retryIds
        return autoCallOrchestrator.startSession(
            clientIds = ids,
            sourceTab = HomeTabs.CLIENTS,
        )
    }

    /**
     * Cancel the active auto-call session. Clears the orchestrator's
     * `session` and any pending countdown so the FAB returns to its
     * idle (re-arm) state. No-op when no session is running.
     */
    fun exitAutoCall() {
        autoCallOrchestrator.exit()
    }

    // ─── Removal action ─────────────────────────────────────────────────────

    /**
     * Remove a client from the list — routed through `agent-status-change`
     * to REMOVED with the chosen [removalReason] (no separate dismissal
     * channel in the 5-state model). Quorum-aware on the backend: a hard
     * reason may keep the client active until a 2nd agent confirms, in
     * which case the reactive flow simply leaves it in place.
     */
    fun dismissClient(
        clientId: String,
        removalReason: RemovalReason,
        freeFormReason: String?,
    ) {
        viewModelScope.launch {
            clientRepository.agentStatusChange(
                clientId = clientId,
                toStatus = ClientStatus.REMOVED,
                removalReason = removalReason,
                reason = freeFormReason,
            )
        }
    }
}
