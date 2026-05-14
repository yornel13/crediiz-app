package com.project.vortex.callsagent.presentation.home

import androidx.lifecycle.ViewModel
import com.project.vortex.callsagent.data.sync.LoginHydrationState
import com.project.vortex.callsagent.domain.call.CallReadiness
import com.project.vortex.callsagent.domain.call.CallReadinessProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val loginHydrationState: LoginHydrationState,
    private val callReadinessProvider: CallReadinessProvider,
) : ViewModel() {

    val isStaleData: StateFlow<Boolean> = loginHydrationState.isStale

    /**
     * Combined VoIP + SIP readiness — drives the persistent banner
     * above the tabs. Three actionable variants (Unassigned /
     * Disconnected / Connecting) plus Ready (banner hidden) plus
     * Unknown (banner hidden, treated as "still loading").
     */
    val callReadiness: StateFlow<CallReadiness> = callReadinessProvider.readiness

    fun dismissStaleBanner() = loginHydrationState.dismiss()

    /** Tap "Reintentar" on the SIP-disconnected banner. */
    fun retrySipRegistration() = callReadinessProvider.retry()
}
