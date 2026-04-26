package com.project.vortex.callsagent.presentation.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.presentation.navigation.RootViewModel
import com.project.vortex.callsagent.ui.theme.Teal700
import com.project.vortex.callsagent.ui.theme.Teal900
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
    val gradient = Brush.verticalGradient(
        colors = listOf(Teal700, Teal900),
    )

    val transition = rememberInfiniteTransition(label = "splash-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-pulse-value",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = Teal700,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Calls Agent",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Getting your queue ready",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Minimum time the splash stays visible. Prevents the auth check from
 * resolving so fast that the screen looks like a glitch.
 */
private const val MIN_DISPLAY_MS = 750L
