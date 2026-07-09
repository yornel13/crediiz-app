package com.project.vortex.callsagent.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceOwnerDataStore by preferencesDataStore(name = "device_owner_prefs")

/**
 * Records WHICH agent the local Room data belongs to — the "data owner".
 *
 * Deliberately a SEPARATE DataStore from [AuthPreferences]: session teardown
 * calls `AuthPreferences.clear()`, which wipes its whole store. The owner
 * mark must survive logout — it is the key that lets the next login decide
 * whether the on-device data can be kept (same agent returns) or must be
 * wiped (different agent takes over the tablet). Putting the key inside
 * AuthPreferences and special-casing clear() would silently break the first
 * time someone "fixes" clear() back to a whole-store wipe.
 *
 * Written only by the login path (identity-keyed wipe) and by the sync
 * identity guard's one-time adoption for devices that predate ownership
 * tracking. Never cleared on logout.
 */
@Singleton
class DeviceOwnerPreferences @Inject constructor(
    private val context: Context,
) {
    private val dataStore = context.deviceOwnerDataStore

    /** Agent id that owns the current Room contents, or null if never set. */
    suspend fun currentOwner(): String? = dataStore.data.first()[KEY_DATA_OWNER_AGENT_ID]

    suspend fun setOwner(agentId: String) {
        dataStore.edit { prefs -> prefs[KEY_DATA_OWNER_AGENT_ID] = agentId }
    }

    companion object {
        private val KEY_DATA_OWNER_AGENT_ID = stringPreferencesKey("data_owner_agent_id")
    }
}
