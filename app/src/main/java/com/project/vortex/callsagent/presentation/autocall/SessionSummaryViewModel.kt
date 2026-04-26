package com.project.vortex.callsagent.presentation.autocall

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    private val orchestrator: AutoCallOrchestrator,
) : ViewModel() {

    val session: StateFlow<AutoCallSession?> = orchestrator.session

    /** Called by the Done button — clears the session so a fresh one can start. */
    fun finish() = orchestrator.exit()
}
