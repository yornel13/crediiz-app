package com.project.vortex.callsagent.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

/**
 * User-facing app settings (things the agent toggles themselves).
 */
@Singleton
class SettingsPreferences @Inject constructor(
    private val context: Context,
) {
    private val dataStore = context.settingsDataStore

    /**
     * Auto-advance mode in auto-call flow:
     * - true  → after NO_ANSWER/BUSY, auto-dial next client after a countdown.
     * - false → always stop at Pre-Call for the next client.
     */
    val autoAdvanceFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_ADVANCE] ?: DEFAULT_AUTO_ADVANCE
    }

    suspend fun setAutoAdvance(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_ADVANCE] = enabled }
    }

    companion object {
        private val KEY_AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        private const val DEFAULT_AUTO_ADVANCE = true
    }
}
