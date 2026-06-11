package com.project.vortex.callsagent

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import com.project.vortex.callsagent.data.voip.VoipRefreshOrchestrator
import com.project.vortex.callsagent.domain.repository.AuthRepository
import com.project.vortex.callsagent.presentation.common.LocalWindowSizeClass
import com.project.vortex.callsagent.presentation.navigation.AppNavGraph
import com.project.vortex.callsagent.presentation.onboarding.OnboardingActivity
import com.project.vortex.callsagent.presentation.onboarding.OnboardingGate
import com.project.vortex.callsagent.presentation.onboarding.OnboardingSessionState
import com.project.vortex.callsagent.ui.locale.LocaleAwareActivity
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import com.project.vortex.callsagent.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : LocaleAwareActivity() {

    @Inject lateinit var settingsPreferences: SettingsPreferences
    @Inject lateinit var onboardingGate: OnboardingGate
    @Inject lateinit var onboardingSessionState: OnboardingSessionState
    @Inject lateinit var voipRefreshOrchestrator: VoipRefreshOrchestrator
    @Inject lateinit var authRepository: AuthRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeLocaleChanges(settingsPreferences)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsPreferences.themeModeFlow
                .collectAsState(initial = ThemeMode.SYSTEM)

            // Compute once at the root and broadcast to the whole tree.
            // Screens consume LocalWindowSizeClass instead of measuring
            // themselves — keeps adaptive logic uniform across the app.
            val windowSizeClass = calculateWindowSizeClass(this)

            // Keep-screen-on enforcement. Active iff the user toggled it
            // ON in Settings AND there's a valid session. We gate on
            // login because keeping the screen lit during the login
            // screen drains battery for no benefit; the agent isn't
            // working yet. onDispose clears the flag so finishing the
            // activity never leaves the device stuck awake.
            val keepScreenOn by combine(
                settingsPreferences.keepScreenOnFlow,
                authRepository.isLoggedIn(),
            ) { enabled, loggedIn -> enabled && loggedIn }
                .collectAsState(initial = false)

            DisposableEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            CallsAgendsTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Mandatory gate — bounce to onboarding while anything is missing.
        //  - A missing REQUIRED permission always bounces (covers cold
        //    start, returning from Settings, and post-revocation).
        //  - A missing OPTIONAL permission (e.g. Bluetooth) also bounces,
        //    but only until the user dismisses onboarding this session —
        //    the session flag prevents an immediate re-bounce loop after
        //    "Continue", while a fresh launch re-offers it.
        val missingRequired = !onboardingGate.allMet()
        val missingOptional = !onboardingGate.allGranted()
        if (missingRequired ||
            (missingOptional && !onboardingSessionState.dismissedThisSession)
        ) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        // Trigger a (debounced) VoIP refresh so the agent picks up
        // admin-side reassignments without restarting the app.
        // No-op when there's no logged-in session — the call to
        // `/voip-accounts/me` 401s and the interceptor handles it.
        voipRefreshOrchestrator.onForeground()
    }
}
