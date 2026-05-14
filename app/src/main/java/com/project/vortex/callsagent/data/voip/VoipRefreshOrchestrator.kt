package com.project.vortex.callsagent.data.voip

import android.util.Log
import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoipRefreshOrchestrator"

/**
 * Triggers VoIP credential fetches at the right moments and re-runs
 * SIP REGISTER whenever the cache changes.
 *
 * **Triggers (callers):**
 *  - `[onLoginSuccess]`  — invoked from [LoginViewModel] right after a
 *    successful login + hydration.
 *  - `[onForeground]`    — invoked from `MainActivity.onResume()`.
 *  - the periodic job (3h) — internal, started in [start].
 *
 * **What it does on each refresh:**
 *  1. Fetch `/voip-accounts/me` via the repository.
 *  2. If the result is [VoipAvailability.Available], call
 *     `coreManager.register()` (idempotent, safe to repeat).
 *  3. If [VoipAvailability.Unassigned], leave the SIP engine alone —
 *     the UI gates calls and the agent contacts the admin.
 *
 * The mutex serializes refreshes so two near-simultaneous triggers
 * (e.g. login + ON_RESUME firing back-to-back) don't double-fetch.
 *
 * **Cleanup:** [stop] cancels the periodic job. Called from
 * `AuthRepository.logout()` (and the session-invalidation cleanup
 * path) so a logged-out app doesn't keep hammering the endpoint.
 */
@Singleton
class VoipRefreshOrchestrator @Inject constructor(
    private val repository: VoipAccountRepository,
    private val coreManager: LinphoneCoreManager,
    private val authPreferences: AuthPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private var periodicJob: Job? = null

    /** Last attempted refresh — used by [onForeground] to debounce. */
    @Volatile
    private var lastRefreshAtMs: Long = 0L

    fun start() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            // First periodic tick is one PERIOD after start; the
            // immediate post-login refresh is handled by onLoginSuccess.
            while (true) {
                delay(PERIODIC_INTERVAL_MS)
                runRefresh(reason = "periodic")
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
        // Tear the SIP core down too — without credentials it has
        // nothing useful to do, and keeping it alive across logout
        // would leak the iterate thread.
        coreManager.stop()
    }

    /** Called once after the login hydrate phase succeeds. */
    fun onLoginSuccess() {
        start()
        scope.launch { runRefresh(reason = "post-login") }
    }

    /**
     * Called every time the app reaches foreground. Debounced so
     * tab-switching the device a few times in a minute doesn't burn
     * the endpoint.
     */
    fun onForeground() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshAtMs < FOREGROUND_DEBOUNCE_MS) return
        scope.launch { runRefresh(reason = "foreground") }
    }

    private suspend fun runRefresh(reason: String) {
        // Skip when there's no token. Hitting `/voip-accounts/me`
        // without a Bearer would 401 and the SessionInvalidation
        // interceptor would interpret that as an expired session,
        // bouncing the user to /login with a misleading banner.
        if (authPreferences.currentToken().isNullOrBlank()) {
            Log.d(TAG, "Refresh skipped ($reason) — no auth token")
            return
        }
        refreshLock.withLock {
            lastRefreshAtMs = System.currentTimeMillis()
            Log.d(TAG, "Refreshing VoIP account ($reason)")
            repository.refresh()
                .onSuccess { availability ->
                    when (availability) {
                        is VoipAvailability.Available -> {
                            Log.d(TAG, "VoIP available — kicking SIP REGISTER")
                            // register() is idempotent + handles credential
                            // changes (clearAccounts/clearAllAuthInfo on each
                            // call). See LinphoneCoreManager.applyAccountAndRegister.
                            coreManager.register()
                        }
                        VoipAvailability.Unassigned -> {
                            Log.w(TAG, "VoIP unassigned — SIP register suppressed")
                        }
                        VoipAvailability.Unknown -> Unit
                    }
                }
                .onFailure { err ->
                    Log.w(TAG, "VoIP refresh failed ($reason): ${err.message}")
                }
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    companion object {
        private val PERIODIC_INTERVAL_MS = TimeUnit.HOURS.toMillis(3)
        private val FOREGROUND_DEBOUNCE_MS = TimeUnit.SECONDS.toMillis(60)
    }
}
