package com.project.vortex.callsagent.data.crash

import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipRegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds context into crash reports so a fatal is actionable without device
 * access:
 *  - [CrashReporter.setUserId] with the logged-in agent id → every crash shows
 *    WHICH agent hit it (the "algunos agentes" question).
 *  - A breadcrumb + custom key for each SIP registration transition → every
 *    crash carries the SIP state trail, doubling as remote visibility into the
 *    "Conectando con el servidor de llamadas…" issue.
 *
 * The Firebase dependency is hidden behind [CrashReporter] and the observed
 * flows are constructor-injected, so the wiring is fully unit-testable.
 */
@Singleton
class CrashContextReporter internal constructor(
    private val reporter: CrashReporter,
    private val agentId: Flow<String?>,
    private val registrationState: Flow<SipRegistrationState>,
) {
    @Inject
    constructor(
        reporter: CrashReporter,
        authPreferences: AuthPreferences,
        coreManager: LinphoneCoreManager,
    ) : this(
        reporter = reporter,
        agentId = authPreferences.agentIdFlow,
        registrationState = coreManager.registrationState,
    )

    /** Begin attaching context on [scope]. Safe for the process lifetime. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        launch {
            agentId.distinctUntilChanged().collect { id ->
                reporter.setUserId(id.orEmpty())
            }
        }
        launch {
            registrationState.distinctUntilChanged().collect { state ->
                val label = label(state)
                // The custom key is overwritten each time, so it always shows
                // the CURRENT SIP state on a crash — no accumulation.
                reporter.setKey(KEY_SIP_REGISTRATION, label)
                // Breadcrumb ONLY for meaningful outcomes. During an outage the
                // watchdog's backoff churns InProgress/Idle/Cleared rapidly;
                // logging each would flood Crashlytics' rolling log buffer and
                // evict other useful breadcrumbs. Registered/Failed carry the
                // signal (recovered / why it dropped).
                if (state is SipRegistrationState.Registered ||
                    state is SipRegistrationState.Failed
                ) {
                    reporter.log("SIP registration → $label")
                }
            }
        }
    }

    private fun label(state: SipRegistrationState): String = when (state) {
        SipRegistrationState.Idle -> "Idle"
        SipRegistrationState.InProgress -> "InProgress"
        SipRegistrationState.Registered -> "Registered"
        SipRegistrationState.Cleared -> "Cleared"
        is SipRegistrationState.Failed -> "Failed(${state.message})"
    }

    companion object {
        private const val KEY_SIP_REGISTRATION = "sip_registration"
    }
}
