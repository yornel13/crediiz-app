package com.project.vortex.callsagent.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.ui.theme.Emerald600
import com.project.vortex.callsagent.ui.theme.PhoneGreen

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onAction: (OnboardingStep) -> Unit,
    onAllMetContinue: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val systemInsets = WindowInsets.systemBars.asPaddingValues()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = systemInsets.calculateTopPadding() + 16.dp,
                bottom = systemInsets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("hero") { Hero() }
            item("subtitle") { SubtitleBlock() }
            items(OnboardingStep.values().toList(), key = { it.name }) { step ->
                StepCard(
                    step = step,
                    granted = state.isMet(step),
                    hardDenied = step in state.hardDenied,
                    onAction = { onAction(step) },
                )
            }
            item("continue") {
                ContinueButton(
                    enabled = state.allMet,
                    onClick = onAllMetContinue,
                )
            }
        }
    }
}

@Composable
private fun Hero() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            text = "Set up Calls Agent",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "We need a few permissions to place and receive calls. " +
                "Grant them all to continue.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubtitleBlock() {
    Text(
        text = "Required permissions",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun StepCard(
    step: OnboardingStep,
    granted: Boolean,
    hardDenied: Boolean,
    onAction: () -> Unit,
) {
    val info = step.info()
    val containerColor =
        if (granted) Emerald600.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    val iconBgColor =
        if (granted) Emerald600.copy(alpha = 0.20f)
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconTint =
        if (granted) Emerald600 else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.Check else info.icon,
                    contentDescription = null,
                    tint = iconTint,
                )
            }
            Spacer(Modifier.height(0.dp))
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            ) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hardDenied && !granted) {
                    Text(
                        text = "Denied — open Settings to grant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(0.dp))
            if (granted) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald600,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (hardDenied) "Settings" else "Allow",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneGreen,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = if (enabled) "Continue" else "Grant all permissions to continue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class StepInfo(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private fun OnboardingStep.info(): StepInfo = when (this) {
    OnboardingStep.DIALER_ROLE -> StepInfo(
        title = "Set as default Phone app",
        description = "Calls Agent must be the system phone app to place and " +
            "receive calls.",
        icon = Icons.Filled.Star,
    )
    OnboardingStep.CALL_PHONE -> StepInfo(
        title = "Place phone calls",
        description = "Required to dial assigned clients.",
        icon = Icons.Filled.Phone,
    )
    OnboardingStep.ANSWER_PHONE_CALLS -> StepInfo(
        title = "Answer phone calls",
        description = "Lets you accept incoming calls from clients.",
        icon = Icons.Filled.PhoneInTalk,
    )
    OnboardingStep.MODIFY_AUDIO_SETTINGS -> StepInfo(
        title = "Manage audio",
        description = "Used to route calls to the speaker so you can take notes " +
            "hands-free.",
        icon = Icons.Filled.Mic,
    )
    OnboardingStep.NOTIFICATIONS -> StepInfo(
        title = "Show notifications",
        description = "For follow-up reminders and call status.",
        icon = Icons.Filled.Notifications,
    )
    OnboardingStep.BATTERY_OPTIMIZATION -> StepInfo(
        title = "Run in the background",
        description = "Prevents the system from killing the call service " +
            "during long shifts.",
        icon = Icons.Filled.BatteryFull,
    )
}
