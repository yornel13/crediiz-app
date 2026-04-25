package com.project.vortex.callsagent.presentation.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.repository.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientsUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

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
     * Observes clients, switching between "all pending" and a local search
     * whenever the query changes. Room Flows emit on every underlying change.
     */
    val clients: StateFlow<List<Client>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                clientRepository.observeAssigned(ClientStatus.PENDING)
            } else {
                clientRepository.searchAssigned(ClientStatus.PENDING, query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        refresh()
    }

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
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
}
