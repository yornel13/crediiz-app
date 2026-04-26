package com.project.vortex.callsagent.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.project.vortex.callsagent.ui.theme.ThemeMode
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

    /**
     * Seconds the auto-call countdown overlay waits before dialing the
     * next client. Range: 0..15. A value of 0 means "skip the
     * countdown entirely — dial immediately".
     */
    val autoCallDelayFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_AUTO_CALL_DELAY] ?: DEFAULT_AUTO_CALL_DELAY)
            .coerceIn(MIN_AUTO_CALL_DELAY, MAX_AUTO_CALL_DELAY)
    }

    suspend fun setAutoCallDelay(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_AUTO_CALL_DELAY, MAX_AUTO_CALL_DELAY)
        dataStore.edit { it[KEY_AUTO_CALL_DELAY] = clamped }
    }

    /**
     * App theme override. Defaults to SYSTEM so the device's setting wins.
     */
    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[KEY_THEME_MODE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    companion object {
        private val KEY_AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        private const val DEFAULT_AUTO_ADVANCE = true

        private val KEY_AUTO_CALL_DELAY = intPreferencesKey("auto_call_delay_seconds")
        const val DEFAULT_AUTO_CALL_DELAY = 5
        const val MIN_AUTO_CALL_DELAY = 0
        const val MAX_AUTO_CALL_DELAY = 15

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
