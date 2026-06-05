package com.project.vortex.callsagent.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.data.sync.ConnectivityObserver
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.repository.ClientDismissalRepository
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
    /**
     * [interestLevel] is the current thermometer of the scheduled
     * client (resolved via local lookup against the assigned INTERESTED
     * set). `null` means the client row isn't in the local cache yet —
     * the UI hides the chip in that case.
     */
    data class Scheduled(
        val followUp: FollowUp,
        val interestLevel: InterestLevel?,
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
    private val clientDismissalRepository: ClientDismissalRepository,
    private val noteRepository: NoteRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    /**
     * Map of section → agenda items. Combines:
     *  - scheduled follow-ups grouped by date,
     *  - the "Sin agendar" orphan-INTERESTED set (oldest first),
     *  - a lookup of `clientId → interestLevel` derived from the local
     *    INTERESTED snapshot, so each Scheduled card can render the
     *    thermometer without a per-row DB call.
     */
    val agenda: StateFlow<Map<AgendaSection, List<AgendaItem>>> = combine(
        followUpRepository.observeAgenda(startOfToday()),
        clientRepository.observeUnscheduledInterested(Instant.now()),
        clientRepository.observeAssigned(ClientStatus.INTERESTED),
    ) { followUps, orphans, interested ->
        val levelByClientId = interested.associate { it.id to it.interestLevel }
        buildSections(followUps, orphans, levelByClientId)
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

    private fun startOfToday(): Instant =
        // "Today" = business calendar day, not the agent's calendar.
        // An agent in Caracas at 23:30 is already on "tomorrow"
        // locally, but the agenda's Today bucket must still mean the
        // Panama business day. See BusinessConfig.
        LocalDate.now(BusinessConfig.BUSINESS_TIMEZONE)
            .atStartOfDay(BusinessConfig.BUSINESS_TIMEZONE)
            .toInstant()

    private fun buildSections(
        followUps: List<FollowUp>,
        orphans: List<Client>,
        levelByClientId: Map<String, InterestLevel?>,
    ): Map<AgendaSection, List<AgendaItem>> {
        val result = linkedMapOf<AgendaSection, List<AgendaItem>>()
        groupFollowUpsByDate(followUps).forEach { (section, items) ->
            result[section] = items.map { fu ->
                AgendaItem.Scheduled(
                    followUp = fu,
                    interestLevel = levelByClientId[fu.clientId],
                )
            }
        }
        if (orphans.isNotEmpty()) {
            result[AgendaSection.UNSCHEDULED] = orphans.map { AgendaItem.Unscheduled(it) }
        }
        return result
    }

    private fun groupFollowUpsByDate(items: List<FollowUp>): Map<AgendaSection, List<FollowUp>> {
        // Bucket by the BUSINESS calendar day, not the agent's device
        // day. A follow-up scheduled for "23 May 23:00 Panama" must
        // appear in the Today bucket for both Panama and Venezuelan
        // agents — under device TZ the Caracas agent would see it as
        // "Tomorrow" (Caracas 00:00 next day). See BusinessConfig.
        val zone = BusinessConfig.BUSINESS_TIMEZONE
        val today = LocalDate.now(zone)
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
