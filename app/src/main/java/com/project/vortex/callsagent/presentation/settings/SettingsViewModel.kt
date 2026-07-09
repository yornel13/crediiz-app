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
import com.project.vortex.callsagent.presentation.onboarding.OnboardingGate
import com.project.vortex.callsagent.ui.locale.AppLanguage
import com.project.vortex.callsagent.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Upper bound for the pre-logout PENDING drain (see [SettingsViewModel.logout]). */
private const val LOGOUT_DRAIN_TIMEOUT_MS = 10_000L

data class SettingsUiState(
    val agentName: String = "",
    val agentEmail: String = "",
    val autoAdvance: Boolean = true,
    val autoCallDelaySeconds: Int = SettingsPreferences.DEFAULT_AUTO_CALL_DELAY,
    val pendingCount: Int = 0,
    val lastSync: SyncResult = SyncResult.Idle,
    val isSyncing: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val keepScreenOn: Boolean = false,
    val showFullActivityHistory: Boolean = true,
    /** False when any onboarding permission (required or optional, e.g.
     * Bluetooth) is still ungranted — drives the Settings permissions row. */
    val permissionsGranted: Boolean = true,
    /** True from the sign-out tap until the session teardown finishes —
     * disables the button (no concurrent logouts) and shows progress
     * during the pre-logout PENDING drain (up to 10 s on bad network). */
    val isLoggingOut: Boolean = false,
)

sealed interface SettingsEvent {
    data object LoggedOut : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsPreferences: SettingsPreferences,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val interactionRepository: InteractionRepository,
    private val noteRepository: NoteRepository,
    private val followUpRepository: FollowUpRepository,
    private val onboardingGate: OnboardingGate,
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _pendingCount = MutableStateFlow(0)

    /** Re-evaluated on every screen resume via [refreshPermissions] so the
     * row reflects grants the user made in system Settings or onboarding. */
    private val _permissionsGranted = MutableStateFlow(onboardingGate.allGranted())

    /** Latch for [logout] — set once, never reset (the session ends). */
    private val _isLoggingOut = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.agentNameFlow(),
        authRepository.agentEmailFlow(),
        settingsPreferences.autoAdvanceFlow,
        syncScheduler.observeIsSyncing(),
        syncManager.lastResult,
        _pendingCount,
        settingsPreferences.themeModeFlow,
        settingsPreferences.autoCallDelayFlow,
        settingsPreferences.keepScreenOnFlow,
        settingsPreferences.showFullActivityHistoryFlow,
        settingsPreferences.appLanguageFlow,
        _permissionsGranted,
        _isLoggingOut,
    ) { values ->
        SettingsUiState(
            agentName = (values[0] as String?).orEmpty(),
            agentEmail = (values[1] as String?).orEmpty(),
            autoAdvance = values[2] as Boolean,
            isSyncing = values[3] as Boolean,
            lastSync = values[4] as SyncResult,
            pendingCount = values[5] as Int,
            themeMode = values[6] as ThemeMode,
            autoCallDelaySeconds = values[7] as Int,
            keepScreenOn = values[8] as Boolean,
            showFullActivityHistory = values[9] as Boolean,
            appLanguage = values[10] as AppLanguage,
            permissionsGranted = values[11] as Boolean,
            isLoggingOut = values[12] as Boolean,
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

    fun onAutoCallDelayChange(seconds: Int) {
        viewModelScope.launch { settingsPreferences.setAutoCallDelay(seconds) }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { settingsPreferences.setThemeMode(mode) }
    }

    fun onAppLanguageChange(language: AppLanguage) {
        viewModelScope.launch { settingsPreferences.setAppLanguage(language) }
    }

    fun onKeepScreenOnToggle(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setKeepScreenOn(enabled) }
    }

    fun onShowFullActivityHistoryToggle(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setShowFullActivityHistory(enabled) }
    }

    fun forceSync() {
        syncScheduler.triggerImmediateSync()
    }

    fun logout() {
        // Latch: a second tap (or re-entry) during the drain must not
        // stack concurrent logout sequences.
        if (!_isLoggingOut.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch {
            try {
                // Best-effort drain: push PENDING rows while the JWT is
                // still valid. Bounded so a dead network can't hold the
                // logout hostage; on timeout/failure nothing is lost —
                // Room survives logout and the rows sync on this agent's
                // next login.
                withTimeoutOrNull(LOGOUT_DRAIN_TIMEOUT_MS) { syncManager.syncAll() }
            } finally {
                // The teardown must survive this ViewModel being cleared
                // (back press mid-drain cancels viewModelScope): without
                // NonCancellable the JWT, SIP registration and sync
                // workers would all outlive the "sign out" tap.
                withContext(NonCancellable) {
                    authRepository.logout()
                }
                // trySend: safe inside a cancelled coroutine (not suspend).
                // If the screen is gone nobody collects it — the dead
                // session then routes to login via the auth gate.
                _events.trySend(SettingsEvent.LoggedOut)
            }
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

    /** Re-check permission grants. Called on every screen resume so the
     * row updates after the user returns from system Settings / onboarding. */
    fun refreshPermissions() {
        _permissionsGranted.value = onboardingGate.allGranted()
    }
}
