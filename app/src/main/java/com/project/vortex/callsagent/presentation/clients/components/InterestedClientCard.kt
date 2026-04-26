package com.project.vortex.callsagent.presentation.clients.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Pipeline-led card for the Interesados view. Shows lead-management
 * metadata: last note, time since last contact, attempt count, plus
 * Note + Call actions. The next-scheduled follow-up chip and the
 * Re-schedule action are deferred to P3/P4 (see
 * `docs/CLIENTS_TAB_REDESIGN.md § 10`).
 */
@Composable
fun InterestedClientCard(
    client: Client,
    onOpen: () -> Unit,
    onAddNote: () -> Unit,
    onCall: () -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name = client.name, size = 48.dp)
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
                StatusPill(
                    label = ClientStatus.INTERESTED.label(),
                    palette = ClientStatus.INTERESTED.palette(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                client.lastNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = "Last note: \"${note.take(120)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = pipelineMeta(client),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onAddNote) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Add note", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onCall) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Call", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun pipelineMeta(client: Client): String {
    val attemptsLabel = "${client.callAttempts} ${if (client.callAttempts == 1) "attempt" else "attempts"}"
    val lastCalled = client.lastCalledAt?.let { "Last called ${formatLongRelative(it)}" }
    return listOfNotNull(lastCalled, attemptsLabel).joinToString(" · ")
}

/**
 * Verbose relative formatter for the Interesados pipeline. Same idea
 * as the Recientes one but extended past 24 h to days/weeks since
 * leads can stay warm for a while.
 */
private fun formatLongRelative(instant: Instant): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> "${minutes / (60 * 24 * 7)}w ago"
    }
}
