package com.project.vortex.callsagent.data.crash

import android.os.Debug
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

/** App memory snapshot attached to crash keys. Values in MB. */
internal data class MemoryStats(val jvmHeapMb: Long, val nativeHeapMb: Long)

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Default production sampler: JVM heap in use + native (malloc) heap.
 * The native figure is the interesting one for Linphone — a steady climb
 * across calls points at a native leak feeding the near-OOM SIGSEGVs.
 */
private fun sampleMemoryStats(): MemoryStats {
    val rt = Runtime.getRuntime()
    return MemoryStats(
        jvmHeapMb = (rt.totalMemory() - rt.freeMemory()) / BYTES_PER_MB,
        nativeHeapMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB,
    )
}

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
    private val callsPlaced: Flow<Int>,
    private val memoryStats: () -> MemoryStats = ::sampleMemoryStats,
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
        callsPlaced = coreManager.callsPlaced,
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
        launch {
            // Per placed call: cumulative count + memory snapshot. On a
            // crash these show whether it correlates with call churn
            // (calls_since_start high) or memory exhaustion (heap keys
            // climbing) — the two patterns seen on the SIGSEGV events.
            callsPlaced.distinctUntilChanged().collect { count ->
                if (count == 0) return@collect // process start — nothing to report yet
                val mem = memoryStats()
                reporter.setKey(KEY_CALLS_SINCE_START, count.toString())
                reporter.setKey(KEY_JVM_HEAP_MB, mem.jvmHeapMb.toString())
                reporter.setKey(KEY_NATIVE_HEAP_MB, mem.nativeHeapMb.toString())
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
        private const val KEY_CALLS_SINCE_START = "calls_since_start"
        private const val KEY_JVM_HEAP_MB = "app_jvm_heap_mb"
        private const val KEY_NATIVE_HEAP_MB = "app_native_heap_mb"
    }
}
