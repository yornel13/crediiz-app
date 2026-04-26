package com.project.vortex.callsagent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.presentation.navigation.AppNavGraph
import com.project.vortex.callsagent.presentation.onboarding.OnboardingActivity
import com.project.vortex.callsagent.presentation.onboarding.OnboardingGate
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import com.project.vortex.callsagent.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsPreferences: SettingsPreferences
    @Inject lateinit var onboardingGate: OnboardingGate

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

    override fun onResume() {
        super.onResume()
        // Mandatory gate — if any dialer requirement is missing (permission
        // revoked, role lost, etc.), bounce to onboarding. The gate covers
        // cold start, returning from Settings, and post-revocation cases.
        if (!onboardingGate.allMet()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }
}
