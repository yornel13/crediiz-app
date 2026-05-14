package com.project.vortex.callsagent.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.project.vortex.callsagent.data.remote.dto.VoipAccountDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voipDataStore by preferencesDataStore(name = "voip_prefs")

/**
 * Local cache of the VoIP credentials assigned to the agent.
 *
 * **Storage decision:** plain DataStore — same pattern as
 * `AuthPreferences`. The JWT lives there in clear text already, so
 * encrypting only the SIP password would be asymmetric security
 * theater. If/when both secrets are migrated to encrypted storage,
 * they should move together. See `calls-core/docs/SECURITY_DEBT.md
 * § SD-1`.
 *
 * Persisted fields are exactly what the SIP engine needs to register
 * (`sipUsername`, `sipPassword`, `sipDomain`) plus a few diagnostics
 * (`did`, `label`) for support workflows. The full DTO doesn't need
 * persisting; its nested `agentId` and `provider` are not consumed
 * locally.
 */
@Singleton
class VoipPreferences @Inject constructor(
    private val context: Context,
) {
    private val dataStore = context.voipDataStore

    val cachedAccountFlow: Flow<CachedVoipAccount?> = dataStore.data.map { prefs ->
        val username = prefs[KEY_SIP_USERNAME]
        val password = prefs[KEY_SIP_PASSWORD]
        val domain = prefs[KEY_SIP_DOMAIN]
        if (username.isNullOrBlank() || password.isNullOrBlank() || domain.isNullOrBlank()) {
            null
        } else {
            CachedVoipAccount(
                sipUsername = username,
                sipPassword = password,
                sipDomain = domain,
                did = prefs[KEY_DID].orEmpty(),
                label = prefs[KEY_LABEL],
            )
        }
    }

    /**
     * Whether the last fetch returned `404 Unassigned`. Persisted so a
     * cold start without network still surfaces the correct gating UI
     * for an agent the admin previously un-assigned.
     */
    val unassignedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_UNASSIGNED] ?: false
    }

    suspend fun saveAccount(dto: VoipAccountDto) {
        dataStore.edit { prefs ->
            prefs[KEY_SIP_USERNAME] = dto.sipUsername
            prefs[KEY_SIP_PASSWORD] = dto.sipPassword
            prefs[KEY_SIP_DOMAIN] = dto.sipDomain
            prefs[KEY_DID] = dto.did
            dto.label?.let { prefs[KEY_LABEL] = it } ?: prefs.remove(KEY_LABEL)
            prefs[KEY_UNASSIGNED] = false
        }
    }

    /**
     * Mark "no VoIP account assigned" without dropping any cached
     * credentials. Some callers (e.g. logout) prefer [clear] which
     * wipes everything; the orchestrator's 404 handling uses this
     * variant so a brief backend hiccup doesn't lose the cache.
     */
    suspend fun markUnassigned() {
        dataStore.edit { prefs ->
            prefs[KEY_UNASSIGNED] = true
            prefs.remove(KEY_SIP_USERNAME)
            prefs.remove(KEY_SIP_PASSWORD)
            prefs.remove(KEY_SIP_DOMAIN)
            prefs.remove(KEY_DID)
            prefs.remove(KEY_LABEL)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Blocking read — used by the SIP credentials provider. */
    suspend fun currentAccount(): CachedVoipAccount? = cachedAccountFlow.first()

    companion object {
        private val KEY_SIP_USERNAME = stringPreferencesKey("sip_username")
        private val KEY_SIP_PASSWORD = stringPreferencesKey("sip_password")
        private val KEY_SIP_DOMAIN = stringPreferencesKey("sip_domain")
        private val KEY_DID = stringPreferencesKey("did")
        private val KEY_LABEL = stringPreferencesKey("label")
        private val KEY_UNASSIGNED = booleanPreferencesKey("unassigned")
    }
}

/**
 * Subset of the VoIP account that we actually persist. Kept as a
 * separate type so the SIP layer doesn't pull in the wire DTO.
 */
data class CachedVoipAccount(
    val sipUsername: String,
    val sipPassword: String,
    val sipDomain: String,
    val did: String,
    val label: String?,
)
