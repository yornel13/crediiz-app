package com.project.vortex.callsagent.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.data.sync.SyncManager
import com.project.vortex.callsagent.data.sync.SyncResult
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.repository.AuthRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val agentName: String = "",
    val agentEmail: String = "",
    val autoAdvance: Boolean = true,
    val pendingCount: Int = 0,
    val lastSync: SyncResult = SyncResult.Idle,
    val isSyncing: Boolean = false,
)

sealed interface SettingsEvent {
    data object LoggedOut : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsPreferences: SettingsPreferences,
    private val syncScheduler: SyncScheduler,
    syncManager: SyncManager,
    private val interactionRepository: InteractionRepository,
    private val noteRepository: NoteRepository,
    private val followUpRepository: FollowUpRepository,
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _pendingCount = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.agentNameFlow(),
        authRepository.agentEmailFlow(),
        settingsPreferences.autoAdvanceFlow,
        syncScheduler.observeIsSyncing(),
        syncManager.lastResult,
        _pendingCount,
    ) { values ->
        SettingsUiState(
            agentName = (values[0] as String?).orEmpty(),
            agentEmail = (values[1] as String?).orEmpty(),
            autoAdvance = values[2] as Boolean,
            isSyncing = values[3] as Boolean,
            lastSync = values[4] as SyncResult,
            pendingCount = values[5] as Int,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        refreshPendingCount()
    }

    fun onAutoAdvanceToggle(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setAutoAdvance(enabled) }
    }

    fun forceSync() {
        syncScheduler.triggerImmediateSync()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            syncScheduler.cancelAll()
            _events.send(SettingsEvent.LoggedOut)
        }
    }

    fun refreshPendingCount() {
        viewModelScope.launch {
            val total = interactionRepository.countPending() +
                noteRepository.countPending() +
                followUpRepository.countPending()
            _pendingCount.value = total
        }
    }
}
