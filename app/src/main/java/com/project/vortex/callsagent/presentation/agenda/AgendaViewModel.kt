package com.project.vortex.callsagent.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Logical buckets for the agenda UI.
 */
enum class AgendaSection { TODAY, TOMORROW, THIS_WEEK, LATER }

data class AgendaUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val followUpRepository: FollowUpRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    /**
     * Map of section → follow-ups. We start from the beginning of today so
     * entries that were scheduled earlier in the day still appear.
     */
    val agenda: StateFlow<Map<AgendaSection, List<FollowUp>>> =
        followUpRepository.observeAgenda(startOfToday())
            .map { groupByAgendaSection(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    init {
        refresh()
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

    private fun groupByAgendaSection(items: List<FollowUp>): Map<AgendaSection, List<FollowUp>> {
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
val Map<AgendaSection, List<FollowUp>>.todayCount: Int
    get() = this[AgendaSection.TODAY]?.size ?: 0

/** Count of days between now and an instant, for relative display. */
fun Instant.daysFromNow(zone: ZoneId = ZoneId.systemDefault()): Long {
    val today = LocalDate.now(zone)
    val target = this.atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, target)
}
