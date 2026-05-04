package com.project.vortex.callsagent.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.data.sync.ConnectivityObserver
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.repository.ClientDismissalRepository
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
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
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Logical buckets for the agenda UI. Order matters — they render in
 * declaration order, with `UNSCHEDULED` last as the safety-net
 * surface for orphan INTERESTED leads.
 */
enum class AgendaSection { TODAY, TOMORROW, THIS_WEEK, LATER, UNSCHEDULED }

/**
 * Agenda items can be either a scheduled follow-up or an orphan
 * INTERESTED client (no pending follow-up). The screen renders each
 * with its own card variant.
 */
sealed interface AgendaItem {
    data class Scheduled(val followUp: FollowUp) : AgendaItem
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
    private val clientDismissalRepository: ClientDismissalRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    /**
     * Map of section → agenda items. Combines scheduled follow-ups
     * (grouped by date) with the "Sin agendar" orphan-INTERESTED set
     * (oldest assigned first).
     */
    val agenda: StateFlow<Map<AgendaSection, List<AgendaItem>>> = combine(
        followUpRepository.observeAgenda(startOfToday()),
        clientRepository.observeUnscheduledInterested(Instant.now()),
    ) { followUps, orphans ->
        buildSections(followUps, orphans)
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

    fun dismissClient(
        clientId: String,
        reasonCode: DismissalReasonCode?,
        freeFormReason: String?,
    ) {
        viewModelScope.launch {
            clientDismissalRepository.dismiss(clientId, reasonCode, freeFormReason)
        }
    }

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

    private fun startOfToday(): Instant =
        LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

    private fun buildSections(
        followUps: List<FollowUp>,
        orphans: List<Client>,
    ): Map<AgendaSection, List<AgendaItem>> {
        val result = linkedMapOf<AgendaSection, List<AgendaItem>>()
        groupFollowUpsByDate(followUps).forEach { (section, items) ->
            result[section] = items.map { AgendaItem.Scheduled(it) }
        }
        if (orphans.isNotEmpty()) {
            result[AgendaSection.UNSCHEDULED] = orphans.map { AgendaItem.Unscheduled(it) }
        }
        return result
    }

    private fun groupFollowUpsByDate(items: List<FollowUp>): Map<AgendaSection, List<FollowUp>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val endOfWeek = today.plusDays(6) // today + 6 more days = "this week"

        return items
            .groupBy { followUp ->
                val date = followUp.scheduledAt.atZone(zone).toLocalDate()
                when {
                    date == today -> AgendaSection.TODAY
                    date == tomorrow -> AgendaSection.TOMORROW
                    date <= endOfWeek -> AgendaSection.THIS_WEEK
                    else -> AgendaSection.LATER
                }
            }
            .mapValues { (_, list) -> list.sortedBy { it.scheduledAt } }
            .toSortedMap(compareBy { it.ordinal })
    }
}

/** Count of today's follow-ups for badge display. */
val Map<AgendaSection, List<AgendaItem>>.todayCount: Int
    get() = this[AgendaSection.TODAY]?.size ?: 0

/** Count of orphan INTERESTED leads for the "Sin agendar" header. */
val Map<AgendaSection, List<AgendaItem>>.unscheduledCount: Int
    get() = this[AgendaSection.UNSCHEDULED]?.size ?: 0

/** Count of days between now and an instant, for relative display. */
fun Instant.daysFromNow(zone: ZoneId = ZoneId.systemDefault()): Long {
    val today = LocalDate.now(zone)
    val target = this.atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, target)
}
