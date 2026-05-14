package com.project.vortex.callsagent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.domain.auth.SessionEventBus
import com.project.vortex.callsagent.domain.auth.SessionInvalidationReason
import com.project.vortex.callsagent.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-shell scoped watcher: subscribes to [SessionEventBus], runs the
 * logout cleanup once per event, and forwards the reason to the
 * navigation layer so it can route to /login with the right copy.
 *
 * Using a Channel (instead of re-exposing the SharedFlow) guarantees
 * the navigation side gets exactly one navigate-to-login per event,
 * even if the composable subscribes late.
 */
@HiltViewModel
class SessionWatcherViewModel @Inject constructor(
    private val sessionEventBus: SessionEventBus,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _navigateToLogin = Channel<SessionInvalidationReason>(Channel.BUFFERED)
    val navigateToLogin = _navigateToLogin.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionEventBus.events.collect { reason ->
                // Cleanup BEFORE navigation so the next /login render
                // doesn't briefly show stale agent state from the prior
                // session (token, name, etc.).
                authRepository.logout()
                _navigateToLogin.trySend(reason)
            }
        }
    }
}
