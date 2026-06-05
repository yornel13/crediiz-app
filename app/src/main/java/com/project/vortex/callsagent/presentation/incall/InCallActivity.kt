package com.project.vortex.callsagent.presentation.incall

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.presentation.common.LocalWindowSizeClass
import com.project.vortex.callsagent.ui.locale.LocaleAwareActivity
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import com.project.vortex.callsagent.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the in-call Compose UI. Lives in its own task so it can be
 * lock-screen friendly and landscape-locked (declared in the manifest).
 *
 * Lifecycle: launched by the call-flow code (see
 * [com.project.vortex.callsagent.domain.call.CallController]). Closes
 * itself once the call has ended (after a brief "Call ended"
 * confirmation) via [InCallScreen]'s `onCallFinished` callback.
 * Navigation to PostCall happens from `AppNavGraph` observing
 * `CallController.lastEndedCall`.
 *
 * Wires up two pieces of state that don't auto-inherit from
 * MainActivity (this activity lives in its own task):
 *   - [LocalWindowSizeClass] — composables inside `InCallScreen`
 *     (e.g. the embedded `PreCallReadOnlyPanel`) read
 *     `WindowSize.*` and would crash otherwise.
 *   - [SettingsPreferences.themeModeFlow] — the agent's chosen
 *     Light/Dark/System preference. Without this, the activity
 *     defaulted to `ThemeMode.SYSTEM` and rendered Light even when
 *     the user had picked Dark in Settings.
 */
@AndroidEntryPoint
class InCallActivity : LocaleAwareActivity() {

    @Inject lateinit var settingsPreferences: SettingsPreferences

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeLocaleChanges(settingsPreferences)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themeMode by settingsPreferences.themeModeFlow
                .collectAsState(initial = ThemeMode.SYSTEM)
            CallsAgendsTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    InCallScreen(onCallFinished = { finish() })
                }
            }
        }
    }
}
