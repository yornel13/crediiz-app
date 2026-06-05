package com.project.vortex.callsagent.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.device.DeviceInfoProvider
import com.project.vortex.callsagent.data.sync.LoginHydrationState
import com.project.vortex.callsagent.data.voip.VoipRefreshOrchestrator
import com.project.vortex.callsagent.domain.error.AuthError
import com.project.vortex.callsagent.domain.repository.AuthRepository
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.result.OperationResult
import com.project.vortex.callsagent.presentation.common.SnackbarMessage
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
    private val deviceInfoProvider: DeviceInfoProvider,
    private val voipRefreshOrchestrator: VoipRefreshOrchestrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Snackbar payloads for blocking errors (account disabled,
     * network, etc.). The recoverable case [AuthError.InvalidCredentials]
     * is shown inline under the form via [LoginUiState.errorMessage]
     * instead — that's where the agent will retype.
     *
     * Capacity = CONFLATED on purpose: if the agent hammers Sign-in
     * with no network, we want the latest error visible once, not a
     * queue of stale snackbars to dismiss one by one.
     */
    private val _snackbar = Channel<SnackbarMessage>(Channel.CONFLATED)
    val snackbarMessages = _snackbar.receiveAsFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            val result = authRepository.login(
                state.email,
                state.password,
                deviceInfoProvider.current(),
            )
            when (result) {
                is OperationResult.Success -> {
                    // Auth succeeded — proceed to hydrate Room from the server.
                    _uiState.update { it.copy(isSubmitting = false, isHydrating = true) }

                    val isStale = hydrate()
                    if (isStale) loginHydrationState.markStale()
                    else loginHydrationState.markFresh()

                    // Phase B: pull VoIP credentials and kick SIP REGISTER.
                    voipRefreshOrchestrator.onLoginSuccess()

                    _uiState.update { it.copy(isHydrating = false) }
                    _events.send(LoginEvent.Success)
                }
                is OperationResult.Failure -> handleAuthFailure(result.error)
            }
        }
    }

    /**
     * Routes a typed [AuthError] to either the inline error field
     * (recoverable, user types again) or a snackbar (blocking issue,
     * the agent needs to know something is wrong but the form isn't
     * the right place to communicate it).
     */
    private suspend fun handleAuthFailure(error: AuthError) {
        when (error) {
            AuthError.InvalidCredentials -> {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = INVALID_CREDENTIALS_MESSAGE,
                    )
                }
            }
            AuthError.AccountDisabled,
            AuthError.DeviceRequired,
            AuthError.SessionExpired,
            AuthError.Network,
            is AuthError.Unknown -> {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = null) }
                _snackbar.send(snackbarFor(error))
            }
        }
    }

    /**
     * Snackbar copy for blocking errors. [AuthError.InvalidCredentials]
     * is intentionally excluded — it is handled inline by
     * [handleAuthFailure] and must never reach this function. Reaching
     * it indicates a routing bug, so we fail loudly instead of
     * silently duplicating the inline copy.
     */
    private fun snackbarFor(error: AuthError): SnackbarMessage = when (error) {
        AuthError.AccountDisabled -> SnackbarMessage(
            textRes = R.string.login_err_account_disabled,
            tone = SnackbarMessage.Tone.ERROR,
        )
        AuthError.DeviceRequired -> SnackbarMessage(
            textRes = R.string.login_err_device_required,
            tone = SnackbarMessage.Tone.ERROR,
        )
        AuthError.SessionExpired -> SnackbarMessage(
            textRes = R.string.login_err_session_expired,
            tone = SnackbarMessage.Tone.ERROR,
        )
        AuthError.Network -> SnackbarMessage(
            textRes = R.string.login_err_network,
            tone = SnackbarMessage.Tone.WARN,
        )
        // Detail goes to telemetry (see ErrorMapper), never to the
        // agent — strings like "Server returned non-RFC9457 error body"
        // are noise for a non-technical user.
        is AuthError.Unknown -> SnackbarMessage(
            textRes = R.string.login_err_unknown,
            tone = SnackbarMessage.Tone.ERROR,
        )
        AuthError.InvalidCredentials -> error(
            "InvalidCredentials must be handled inline; never route it through snackbarFor.",
        )
    }

    private companion object {
        const val INVALID_CREDENTIALS_MESSAGE = "Email o contraseña incorrectos."
    }

    /**
     * Pull assigned clients + agenda from the server in parallel so the agent
     * lands on Home with data already in Room. Returns true if any of the
     * fetches failed (e.g. offline) — caller surfaces a "stale data" banner.
     *
     * All pulls run in parallel — KI-02 is closed (the DAO uses
     * `replaceAllByStatus`, status-scoped, so they don't clobber each
     * other). After the 2026-05 backend refactor (HOW_IT_WORKS §3),
     * the funnel splits PENDING ("never called") from IN_PROGRESS
     * ("called, no closing outcome yet"), so the Retry section needs
     * the IN_PROGRESS pull to populate on first launch. INTERESTED is
     * required for Agenda's INNER JOIN against `clients`.
     */
    private suspend fun hydrate(): Boolean = coroutineScope {
        val pending = async { clientRepository.refreshAssigned(ClientStatus.PENDING) }
        val inProgress = async { clientRepository.refreshAssigned(ClientStatus.IN_PROGRESS) }
        val interested = async { clientRepository.refreshAssigned(ClientStatus.INTERESTED) }
        val agenda = async { followUpRepository.refreshAgenda() }
        val results = listOf(
            pending.await(),
            inProgress.await(),
            interested.await(),
            agenda.await(),
        )
        results.any { it.isFailure }
    }
}
