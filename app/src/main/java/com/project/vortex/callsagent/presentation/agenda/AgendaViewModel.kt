package com.project.vortex.callsagent.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.data.sync.ConnectivityObserver
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import com.project.vortex.callsagent.common.BusinessConfig
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Logical buckets for the agenda UI. Order matters — they render in
 * declaration order:
 *  - [OVERDUE]     "Vencidos": should have been called (EXPIRED, or re-evaluated
 *                  locally as past-due). Shown first.
 *  - [TODAY]       "Programados": due today (Panama day), not yet overdue.
 *  - [UPCOMING]    "Pendientes": tomorrow onward.
 *  - [UNSCHEDULED] "Sin agendar": orphan INTERESTED leads, last (safety net).
 */
enum class AgendaSection { OVERDUE, TODAY, UPCOMING, UNSCHEDULED }

/**
 * Agenda items can be either a scheduled follow-up or an orphan
 * INTERESTED client (no pending follow-up). The screen renders each
 * with its own card variant.
 */
sealed interface AgendaItem {
    data class Scheduled(
        val followUp: FollowUp,
    ) : AgendaItem
    data class Unscheduled(val client: Client) : AgendaItem
}

data class AgendaUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val followUpRepository: FollowUpRepository,
    private val clientRepository: ClientRepository,
    private val noteRepository: NoteRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    /**
     * Map of section → agenda items. Combines:
     *  - scheduled follow-ups grouped by date,
     *  - the "Sin agendar" set: active clients (INTERESTED/CITED) with no
     *    active follow-up (oldest first).
     */
    val agenda: StateFlow<Map<AgendaSection, List<AgendaItem>>> = combine(
        followUpRepository.observeAgenda(),
        clientRepository.observeUnscheduledActive(),
    ) { followUps, orphans ->
        buildAgendaSections(followUps, orphans, Instant.now(), BusinessConfig.BUSINESS_TIMEZONE)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    init {
        refresh()
        observeConnectivityForErrorClear()
    }

    /**
     * Watch connectivity transitions: as soon as the device goes from
     * offline to online, drop any stale "Unable to resolve host…" /
     * network-error message and re-fetch.
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

    /**
     * Remove a client — routed through `agent-status-change` to REMOVED
     * with the chosen [removalReason] (no separate dismissal channel in
     * the 5-state model). Quorum-aware on the backend.
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

    /**
     * One-shot local lookup used by the adaptive detail pane on
     * tablets. Same shape as [com.project.vortex.callsagent.presentation
     * .clients.ClientsViewModel.findClientLocally] — the two screens
     * each own their own VM but resolve clients identically.
     */
    suspend fun findClientLocally(id: String): Client? =
        clientRepository.findById(id)

    /**
     * Live note feed for the adaptive detail pane. See
     * [com.project.vortex.callsagent.presentation.clients.ClientsViewModel
     * .observeNotesForClient] — same shape, replicated here so the
     * detail pane consumes its own tab's VM and doesn't reach
     * sideways across feature packages.
     */
    fun observeNotesForClient(clientId: String) =
        noteRepository.observeByClient(clientId)

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            followUpRepository.refreshAgenda()
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.message ?: "Failed to refresh agenda",
                    )
                }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

}

/**
 * Compose the ordered agenda map from active follow-ups + orphan leads.
 * Sections render in declaration order; empty ones are omitted. Pure top-level
 * fn so it can be unit tested without the ViewModel.
 */
fun buildAgendaSections(
    followUps: List<FollowUp>,
    orphans: List<Client>,
    now: Instant,
    zone: ZoneId,
): Map<AgendaSection, List<AgendaItem>> {
    val result = linkedMapOf<AgendaSection, List<AgendaItem>>()
    val bySection = followUps.groupBy { followUpAgendaSection(it, now, zone) }
    // Declaration order: Vencidos → Programados → Pendientes.
    listOf(AgendaSection.OVERDUE, AgendaSection.TODAY, AgendaSection.UPCOMING).forEach { section ->
        bySection[section]
            ?.sortedBy { it.scheduledAt }
            ?.takeIf { it.isNotEmpty() }
            ?.let { items -> result[section] = items.map { AgendaItem.Scheduled(it) } }
    }
    if (orphans.isNotEmpty()) {
        result[AgendaSection.UNSCHEDULED] = orphans.map { AgendaItem.Unscheduled(it) }
    }
    return result
}

/** Grace window after the scheduled time before a PENDING is treated as overdue. */
private val FOLLOW_UP_EXPIRY_GRACE: java.time.Duration = java.time.Duration.ofHours(1)

/**
 * Re-evaluate a follow-up's overdue state LOCALLY, in Panama time, so a
 * freshly-expired follow-up surfaces without waiting for the next backend sync.
 * Mirrors the backend rule: overdue when >1h past the scheduled time OR the
 * Panama calendar day has rolled over. A backend [FollowUpStatus.EXPIRED] is
 * trusted as-is; terminal states are never overdue.
 */
fun isFollowUpOverdue(followUp: FollowUp, now: Instant, zone: ZoneId): Boolean =
    when (followUp.status) {
        FollowUpStatus.COMPLETED, FollowUpStatus.CANCELLED -> false
        FollowUpStatus.EXPIRED -> true
        FollowUpStatus.PENDING -> {
            val pastGrace = now.isAfter(followUp.scheduledAt.plus(FOLLOW_UP_EXPIRY_GRACE))
            val today = now.atZone(zone).toLocalDate()
            val scheduledDay = followUp.scheduledAt.atZone(zone).toLocalDate()
            pastGrace || scheduledDay.isBefore(today)
        }
    }

/** Which agenda bucket a follow-up belongs to right now (Panama time). */
fun followUpAgendaSection(followUp: FollowUp, now: Instant, zone: ZoneId): AgendaSection {
    if (isFollowUpOverdue(followUp, now, zone)) return AgendaSection.OVERDUE
    val today = now.atZone(zone).toLocalDate()
    val scheduledDay = followUp.scheduledAt.atZone(zone).toLocalDate()
    return if (scheduledDay == today) AgendaSection.TODAY else AgendaSection.UPCOMING
}

/** Count of today's follow-ups for badge display. */
val Map<AgendaSection, List<AgendaItem>>.todayCount: Int
    get() = this[AgendaSection.TODAY]?.size ?: 0

/** Count of orphan INTERESTED leads for the "Sin agendar" header. */
val Map<AgendaSection, List<AgendaItem>>.unscheduledCount: Int
    get() = this[AgendaSection.UNSCHEDULED]?.size ?: 0

/**
 * Count of days between now and an instant, for relative display.
 * Defaults to the business clock so "2 days from now" means the same
 * thing for every agent regardless of where they sit. See BusinessConfig.
 */
fun Instant.daysFromNow(zone: ZoneId = BusinessConfig.BUSINESS_TIMEZONE): Long {
    val today = LocalDate.now(zone)
    val target = this.atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, target)
}
