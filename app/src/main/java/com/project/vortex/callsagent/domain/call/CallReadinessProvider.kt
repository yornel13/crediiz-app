package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipRegistrationState
import com.project.vortex.callsagent.data.voip.VoipAccountRepository
import com.project.vortex.callsagent.data.voip.VoipAvailability
import com.project.vortex.callsagent.data.voip.VoipRefreshOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the UI's "can the agent place a call right
 * now?" question. Folds the VoIP-account availability (backend) and
 * the SIP registration state (Linphone) into one [CallReadiness] flow.
 *
 * The mapping intentionally privileges the **backend** verdict over the
 * SIP one: if the admin un-assigned the agent's Vozelia line, the
 * banner says "Unassigned" even if Linphone is still registered with
 * the previous credentials — the next foreground refresh will
 * unregister, but the UI shouldn't lie in the meantime.
 */
@Singleton
class CallReadinessProvider @Inject constructor(
    private val voipAccountRepository: VoipAccountRepository,
    private val coreManager: LinphoneCoreManager,
    private val voipRefreshOrchestrator: VoipRefreshOrchestrator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val readiness: StateFlow<CallReadiness> =
        combine(
            voipAccountRepository.voipAvailability,
            coreManager.registrationState,
        ) { voip, sip ->
            map(voip, sip)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = CallReadiness.Unknown,
        )

    /**
     * Re-trigger registration after a transient network error. Cheap
     * and idempotent: [LinphoneCoreManager.register] re-uses the
     * cached credentials. Also kicks a backend refresh in case the
     * admin reassigned the line in the meantime.
     */
    fun retry() {
        voipRefreshOrchestrator.onForeground()
        coreManager.register()
    }

    private fun map(
        voip: VoipAvailability,
        sip: SipRegistrationState,
    ): CallReadiness = when (voip) {
        VoipAvailability.Unassigned -> CallReadiness.Unassigned
        VoipAvailability.Unknown -> CallReadiness.Unknown
        is VoipAvailability.Available -> when (sip) {
            SipRegistrationState.Registered -> CallReadiness.Ready
            SipRegistrationState.InProgress -> CallReadiness.Connecting
            SipRegistrationState.Idle,
            SipRegistrationState.Cleared -> CallReadiness.Connecting
            is SipRegistrationState.Failed -> CallReadiness.Disconnected(sip.message)
        }
    }
}
