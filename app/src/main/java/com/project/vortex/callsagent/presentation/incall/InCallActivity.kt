package com.project.vortex.callsagent.presentation.incall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the in-call Compose UI. Lives in its own task so it can be
 * lock-screen friendly and landscape-locked (declared in the manifest).
 *
 * Lifecycle: launched by [com.project.vortex.callsagent.telecom.CallsInCallService.onCallAdded].
 * Closes itself once the call has ended (after a brief "Call ended"
 * confirmation) via [InCallScreen]'s `onCallFinished` callback. Navigation
 * to PostCall happens from `AppNavGraph` observing
 * `CallManager.lastEndedCall`.
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            CallsAgendsTheme {
                InCallScreen(onCallFinished = { finish() })
            }
        }
    }
}
