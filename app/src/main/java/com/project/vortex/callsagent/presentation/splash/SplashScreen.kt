package com.project.vortex.callsagent.presentation.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.presentation.navigation.RootViewModel
import com.project.vortex.callsagent.ui.components.CrediizWordmark
import kotlinx.coroutines.delay

/**
 * Brand splash. Resolves the auth state in the background and routes
 * to either Login or Home as soon as we know — but never sooner than
 * [MIN_DISPLAY_MS] so the screen doesn't feel like a flicker.
 */
@Composable
fun SplashScreen(
    onResolved: (loggedIn: Boolean) -> Unit,
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val isLoggedIn by rootViewModel.isLoggedIn.collectAsState()

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        // Wait for auth state to flip away from null AND for the minimum
        // display time so the splash always feels intentional.
        while (isLoggedIn == null) {
            delay(50)
        }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < MIN_DISPLAY_MS) delay(MIN_DISPLAY_MS - elapsed)
        onResolved(isLoggedIn == true)
    }

    SplashContent()
}

@Composable
private fun SplashContent() {
    val transition = rememberInfiniteTransition(label = "splash-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-pulse-value",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        CrediizWordmark(
            modifier = Modifier.scale(pulse),
            fontSize = 48.sp,
        )
    }
}

/**
 * Minimum time the splash stays visible. Prevents the auth check from
 * resolving so fast that the screen looks like a glitch.
 */
private const val MIN_DISPLAY_MS = 750L
