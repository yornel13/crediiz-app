package com.project.vortex.callsagent.presentation.navigation

import androidx.lifecycle.ViewModel
import com.project.vortex.callsagent.telecom.CallManager
import com.project.vortex.callsagent.telecom.model.EndedCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Surfaces [CallManager.lastEndedCall] to [AppNavGraph] without leaking
 * the manager into the graph composable directly. The graph observes
 * [endedCall] and navigates to PostCall when it becomes non-null, then
 * calls [consumeEndedCall] to clear the signal.
 */
@HiltViewModel
class CallNavigationViewModel @Inject constructor(
    private val callManager: CallManager,
) : ViewModel() {

    val endedCall: StateFlow<EndedCall?> = callManager.lastEndedCall

    fun consumeEndedCall() = callManager.consumeLastEndedCall()
}
