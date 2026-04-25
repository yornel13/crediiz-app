package com.project.vortex.callsagent.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.sync.LoginHydrationState
import com.project.vortex.callsagent.domain.repository.AuthRepository
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val isHydrating: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.isNotBlank() &&
            !isSubmitting &&
            !isHydrating

    val isBusy: Boolean get() = isSubmitting || isHydrating
}

sealed interface LoginEvent {
    data object Success : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val clientRepository: ClientRepository,
    private val followUpRepository: FollowUpRepository,
    private val loginHydrationState: LoginHydrationState,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            authRepository.login(state.email, state.password)
                .onSuccess {
                    // Auth succeeded — proceed to hydrate Room from the server.
                    _uiState.update { it.copy(isSubmitting = false, isHydrating = true) }

                    val isStale = hydrate()
                    if (isStale) loginHydrationState.markStale()
                    else loginHydrationState.markFresh()

                    _uiState.update { it.copy(isHydrating = false) }
                    _events.send(LoginEvent.Success)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = throwable.message?.takeIf(String::isNotBlank)
                                ?: "Unable to sign in. Please try again.",
                        )
                    }
                }
        }
    }

    /**
     * Pull assigned clients + agenda from the server in parallel so the agent
     * lands on Home with data already in Room. Returns true if any of the
     * fetches failed (e.g. offline) — caller surfaces a "stale data" banner.
     *
     * Note: only PENDING clients are refreshed here. INTERESTED come in via
     * the regular sync cycle. This avoids tripping KI-02 (consecutive
     * `refreshAssigned` calls erase each other) on the very first launch.
     */
    private suspend fun hydrate(): Boolean = coroutineScope {
        val pending = async { clientRepository.refreshAssigned(ClientStatus.PENDING) }
        val agenda = async { followUpRepository.refreshAgenda() }
        val results = listOf(pending.await(), agenda.await())
        results.any { it.isFailure }
    }
}
