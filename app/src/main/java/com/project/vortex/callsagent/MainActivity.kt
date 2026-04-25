package com.project.vortex.callsagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.presentation.navigation.AppNavGraph
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import com.project.vortex.callsagent.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsPreferences: SettingsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsPreferences.themeModeFlow
                .collectAsState(initial = ThemeMode.SYSTEM)

            CallsAgendsTheme(themeMode = themeMode) {
                AppNavGraph()
            }
        }
    }
}
