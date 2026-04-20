package com.project.vortex.callsagent.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

/**
 * Persists the agent's JWT and identifying info.
 * Reads are async (Flow / suspend) because DataStore is asynchronous by design.
 * The auth interceptor does a blocking read only on outgoing HTTP requests.
 */
@Singleton
class AuthPreferences @Inject constructor(
    private val context: Context,
) {
    private val dataStore = context.authDataStore

    val accessTokenFlow: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val agentIdFlow: Flow<String?> = dataStore.data.map { it[KEY_AGENT_ID] }
    val agentNameFlow: Flow<String?> = dataStore.data.map { it[KEY_AGENT_NAME] }
    val agentEmailFlow: Flow<String?> = dataStore.data.map { it[KEY_AGENT_EMAIL] }

    suspend fun saveSession(token: String, agentId: String, email: String, name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = token
            prefs[KEY_AGENT_ID] = agentId
            prefs[KEY_AGENT_EMAIL] = email
            prefs[KEY_AGENT_NAME] = name
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Blocking read for the auth interceptor. */
    suspend fun currentToken(): String? = dataStore.data.first()[KEY_ACCESS_TOKEN]

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_AGENT_ID = stringPreferencesKey("agent_id")
        private val KEY_AGENT_EMAIL = stringPreferencesKey("agent_email")
        private val KEY_AGENT_NAME = stringPreferencesKey("agent_name")
    }
}
