package com.project.vortex.callsagent.data.voip

import android.util.Log
import com.project.vortex.callsagent.data.local.preferences.VoipPreferences
import com.project.vortex.callsagent.data.remote.api.VoipAccountsApi
import com.project.vortex.callsagent.data.sip.SipConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoipAccountRepository"
private const val HTTP_NOT_FOUND = 404

/**
 * Source of truth for the agent's VoIP credentials. Bridges the
 * remote endpoint, the local cache, and the SIP engine.
 *
 * **Triggers (managed by [VoipRefreshOrchestrator], not this class):**
 *  - just after login,
 *  - when the app returns from background,
 *  - every ~3h while the app is in foreground.
 *
 * **States exposed via [voipAvailability]:**
 *  - [VoipAvailability.Available] — credentials cached and valid.
 *  - [VoipAvailability.Unassigned] — backend returned 404 ("no VoIP
 *    account assigned"). UI gates calls and shows a persistent banner.
 *  - [VoipAvailability.Unknown] — cold start, no cache yet, no
 *    confirmed 404. UI must NOT block — we may be offline.
 *
 * The SIP engine reads via [current], a suspend non-flow accessor for
 * the [SipCredentialsProvider] swap (see `SipModule`).
 */
@Singleton
class VoipAccountRepository @Inject constructor(
    private val api: VoipAccountsApi,
    private val preferences: VoipPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Combined view derived from cache + the "unassigned" sticky flag.
     * UI consumers should observe this flow.
     */
    val voipAvailability: StateFlow<VoipAvailability> =
        combine(
            preferences.cachedAccountFlow,
            preferences.unassignedFlow,
        ) { cached, unassigned ->
            when {
                cached != null -> VoipAvailability.Available(
                    SipConfig(
                        server = cached.sipDomain,
                        user = cached.sipUsername,
                        password = cached.sipPassword,
                    ),
                )
                unassigned -> VoipAvailability.Unassigned
                else -> VoipAvailability.Unknown
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = VoipAvailability.Unknown,
        )

    /**
     * Fetch fresh credentials from the backend and persist them.
     *
     * - 200 → cache replaced, returns [VoipAvailability.Available].
     * - 404 → cache wiped + unassigned flag set, returns
     *   [VoipAvailability.Unassigned].
     * - any other failure → cache untouched, returns Result.failure
     *   so the orchestrator can decide to retry or back off.
     */
    suspend fun refresh(): Result<VoipAvailability> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                val envelope = api.me()
                preferences.saveAccount(envelope.data)
                VoipAvailability.Available(
                    SipConfig(
                        server = envelope.data.sipDomain,
                        user = envelope.data.sipUsername,
                        password = envelope.data.sipPassword,
                    ),
                )
            } catch (http: HttpException) {
                if (http.code() == HTTP_NOT_FOUND) {
                    Log.i(TAG, "Backend reports no VoIP account assigned to this agent")
                    preferences.markUnassigned()
                    VoipAvailability.Unassigned
                } else {
                    throw http
                }
            }
        }
    }

    /**
     * Synchronous read for the SIP credentials provider. Returns the
     * persisted credentials or null if the cache is empty (cold start
     * without prior fetch). The SIP engine treats null as "cannot
     * register yet" — the next refresh + register cycle resolves it.
     */
    suspend fun current(): SipConfig? {
        val cached = preferences.currentAccount() ?: return null
        return SipConfig(
            server = cached.sipDomain,
            user = cached.sipUsername,
            password = cached.sipPassword,
        )
    }

    /** Wipe the cache. Called on logout / session invalidation. */
    suspend fun clear() {
        preferences.clear()
    }
}

/**
 * Three-way enumeration of the VoIP credentials' availability.
 *
 * `Unknown` exists as a distinct state from `Unassigned` so the UI
 * can differentiate "we don't know yet" (don't block calls — the
 * agent might be offline) from "backend confirmed there's no account"
 * (block calls + show banner).
 */
sealed interface VoipAvailability {
    data class Available(val config: SipConfig) : VoipAvailability
    data object Unassigned : VoipAvailability
    data object Unknown : VoipAvailability
}
