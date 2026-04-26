package com.project.vortex.callsagent.presentation.clients.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Outcome-led card variant for the Recientes view. Replaces the
 * queue-position metadata of the Pendientes card with time-since-call,
 * outcome badge, and a one-line note preview. See
 * `docs/CLIENTS_TAB_REDESIGN.md § 4.2.2`.
 *
 * The whole card is clickable and opens the client detail (Pre-Call),
 * where Add-note and Call-again actions live. We deliberately do NOT
 * surface those actions on the list row — they bloat the card and
 * the agent already gets them inside the detail.
 */
@Composable
fun RecentClientCard(
    client: Client,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outcome = client.lastOutcome

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
            // ─── Header: avatar + name + time-ago ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutcomeAvatar(name = client.name, outcome = outcome)
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
                client.lastCalledAt?.let {
                    Text(
                        text = formatRelative(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ─── Outcome + note preview ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (outcome != null) {
                        StatusPill(
                            label = outcome.label(),
                            palette = outcome.palette(),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = "${client.callAttempts} ${if (client.callAttempts == 1) "attempt" else "attempts"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                client.lastNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "\"${note.take(80)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Avatar with a small outcome-colored ring. Falls back to a plain
 * Avatar when the outcome is null (shouldn't happen in Recientes but
 * keeps the component defensive).
 */
@Composable
private fun OutcomeAvatar(name: String, outcome: CallOutcome?) {
    val ringColor = outcome?.palette()?.onContainer
        ?: MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(name = name, size = 44.dp)
    }
}

/**
 * Compact relative-time formatter aimed at the last-24h window.
 * "just now" / "12m ago" / "3h ago".
 */
private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
