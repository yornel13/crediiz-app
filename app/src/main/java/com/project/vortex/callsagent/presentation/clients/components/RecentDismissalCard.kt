package com.project.vortex.callsagent.presentation.clients.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.ClientDismissal
import com.project.vortex.callsagent.ui.components.Avatar
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Card variant for a recently dismissed client. Distinct from the
 * outcome-led RecentClientCard to make clear the action was a removal,
 * not a call. Single action: "Deshacer descarte" while inside the
 * 24 h recovery window.
 */
@Composable
fun RecentDismissalCard(
    client: Client,
    dismissal: ClientDismissal,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DismissedAvatar(name = client.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = client.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatRelative(dismissal.dismissedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Status + reason + undo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DESCARTADO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    val reasonText = displayReason(dismissal)
                    if (reasonText != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· $reasonText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Deshacer descarte", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DismissedAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(name = name, size = 44.dp)
        // Overlay X — small, top-right.
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun displayReason(dismissal: ClientDismissal): String? {
    val codeLabel = dismissal.reasonCode?.let { code ->
        runCatching { DismissalReasonCode.valueOf(code).labelEs }.getOrNull()
    }
    val free = dismissal.reason?.takeIf { it.isNotBlank() }
    return when {
        codeLabel != null && free != null -> "$codeLabel — $free"
        codeLabel != null -> codeLabel
        free != null -> "\"$free\""
        else -> null
    }
}

private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> "hace un momento"
        minutes < 60 -> "hace ${minutes}m"
        minutes < 60 * 24 -> "hace ${minutes / 60}h"
        else -> "hace ${minutes / (60 * 24)}d"
    }
}
