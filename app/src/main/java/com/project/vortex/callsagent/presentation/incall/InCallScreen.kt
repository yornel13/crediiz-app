package com.project.vortex.callsagent.presentation.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.telecom.model.CallUiState
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.Teal700
import com.project.vortex.callsagent.ui.theme.Teal900
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun InCallScreen(
    onCallFinished: () -> Unit,
    viewModel: InCallViewModel = hiltViewModel(),
) {
    val callState by viewModel.callState.collectAsState()
    val client by viewModel.currentClient.collectAsState()
    val direction by viewModel.callDirection.collectAsState()
    val incomingPhone by viewModel.incomingPhoneNumber.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaker by viewModel.isSpeakerOn.collectAsState()
    val liveNote by viewModel.liveNoteContent.collectAsState()

    var hasDisconnected by remember { mutableStateOf(false) }
    if (callState is CallUiState.Disconnected) hasDisconnected = true

    LaunchedEffect(hasDisconnected) {
        if (hasDisconnected) {
            delay(1200)
            onCallFinished()
        }
    }

    val gradient = Brush.verticalGradient(colors = listOf(Teal700, Teal900))
    val isIncomingRinging = direction == CallDirection.INBOUND &&
        callState is CallUiState.Ringing

    Surface(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (isIncomingRinging) {
                IncomingRingingContent(
                    client = client,
                    fallbackPhone = incomingPhone.orEmpty(),
                    onAccept = viewModel::acceptIncoming,
                    onReject = viewModel::rejectIncoming,
                )
            } else {
                ActiveCallContent(
                    client = client,
                    fallbackPhone = incomingPhone.orEmpty(),
                    direction = direction,
                    callState = callState,
                    liveNote = liveNote,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeaker,
                    onNoteChange = viewModel::onNoteChange,
                    onToggleMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onEndCall = viewModel::endCall,
                )
            }
        }
    }
}

// ─── Incoming ringing ─────────────────────────────────────────────────────

@Composable
private fun IncomingRingingContent(
    client: Client?,
    fallbackPhone: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        // Header pill — clearly marks this is incoming.
        Surface(
            shape = PillShape,
            color = Color.White.copy(alpha = 0.20f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "INCOMING CALL",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Avatar(name = client?.name ?: "?", size = 112.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = client?.name ?: "Unknown number",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = client?.phone?.takeIf { it.isNotBlank() } ?: fallbackPhone,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.85f),
        )

        Spacer(Modifier.weight(1f))

        // Big accept / reject buttons.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleActionButton(
                icon = Icons.Filled.CallEnd,
                label = "Reject",
                color = MaterialTheme.colorScheme.error,
                onClick = onReject,
            )
            CircleActionButton(
                icon = Icons.Filled.Call,
                label = "Accept",
                color = PhoneGreen,
                onClick = onAccept,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(76.dp),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Active call (outgoing OR accepted incoming) ─────────────────────────

@Composable
private fun ColumnScope.ActiveCallContent(
    client: Client?,
    fallbackPhone: String,
    direction: CallDirection,
    callState: CallUiState,
    liveNote: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onNoteChange: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (direction == CallDirection.INBOUND) {
            DirectionBadge(label = "INCOMING")
            Spacer(Modifier.height(10.dp))
        }
        Avatar(name = client?.name ?: "?", size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = client?.name ?: if (direction == CallDirection.INBOUND) "Unknown number"
            else "Connecting...",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = client?.phone?.takeIf { it.isNotBlank() } ?: fallbackPhone,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(12.dp))
        StatusAndTimer(state = callState)
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "NOTES",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f),
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = liveNote,
        onValueChange = onNoteChange,
        placeholder = {
            Text(
                text = "Capture what the client says…",
                color = Color.White.copy(alpha = 0.5f),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )

    Spacer(Modifier.height(20.dp))

    ControlsRow(
        isMuted = isMuted,
        isSpeakerOn = isSpeakerOn,
        canHangUp = callState !is CallUiState.Disconnected,
        onToggleMute = onToggleMute,
        onToggleSpeaker = onToggleSpeaker,
        onEndCall = onEndCall,
    )
}

@Composable
private fun DirectionBadge(label: String) {
    Surface(
        shape = PillShape,
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatusAndTimer(state: CallUiState) {
    val statusLabel = when (state) {
        CallUiState.Idle -> "Idle"
        CallUiState.Dialing -> "Dialing"
        CallUiState.Ringing -> "Ringing"
        is CallUiState.Active -> "Connected"
        CallUiState.Disconnected -> "Call ended"
    }

    Surface(
        shape = PillShape,
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state is CallUiState.Active) PhoneGreen else Color.White),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    if (state is CallUiState.Active) {
        LiveTimer(activeSince = state.activeSince)
    } else {
        Text(
            text = "—",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LiveTimer(activeSince: Instant) {
    var elapsed by remember { mutableStateOf(elapsedSeconds(activeSince)) }
    LaunchedEffect(activeSince) {
        while (true) {
            elapsed = elapsedSeconds(activeSince)
            delay(500)
        }
    }
    Text(
        text = formatElapsed(elapsed),
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ControlsRow(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    canHangUp: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleControl(
            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            label = if (isMuted) "Muted" else "Mute",
            active = isMuted,
            onClick = onToggleMute,
        )
        ToggleControl(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "Speaker",
            active = isSpeakerOn,
            onClick = onToggleSpeaker,
        )
        EndCallButton(enabled = canHangUp, onClick = onEndCall)
    }
}

@Composable
private fun ToggleControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(64.dp),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) Teal900 else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EndCallButton(enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(width = 96.dp, height = 64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            ),
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End call")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "End",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun elapsedSeconds(from: Instant): Long =
    Duration.between(from, Instant.now()).seconds.coerceAtLeast(0)

private fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
