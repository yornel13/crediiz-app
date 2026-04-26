package com.project.vortex.callsagent.presentation.agenda

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.presentation.clients.components.DismissClientSheet
import com.project.vortex.callsagent.ui.components.Avatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun AgendaScreen(
    onFollowUpSelected: (String) -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val agenda by viewModel.agenda.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var dismissTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (agenda.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uiState.isRefreshing) "Loading agenda..." else "No follow-ups scheduled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    agenda.forEach { (section, items) ->
                        item(key = "header-${section.name}") {
                            SectionHeader(section, items.size)
                        }
                        items(items, key = { it.stableKey() }) { item ->
                            when (item) {
                                is AgendaItem.Scheduled -> FollowUpRow(
                                    followUp = item.followUp,
                                    onClick = { onFollowUpSelected(item.followUp.clientId) },
                                )
                                is AgendaItem.Unscheduled -> UnscheduledRow(
                                    client = item.client,
                                    onClick = { onFollowUpSelected(item.client.id) },
                                    onDismiss = {
                                        dismissTarget = item.client.id to item.client.name
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        dismissTarget?.let { (id, name) ->
            DismissClientSheet(
                clientName = name,
                onDismiss = { dismissTarget = null },
                onConfirm = { code, free ->
                    viewModel.dismissClient(id, code, free)
                    dismissTarget = null
                },
            )
        }
    }
}

private fun AgendaItem.stableKey(): String = when (this) {
    is AgendaItem.Scheduled -> "fu:${followUp.mobileSyncId}"
    is AgendaItem.Unscheduled -> "uns:${client.id}"
}

@Composable
private fun SectionHeader(section: AgendaSection, count: Int) {
    val label = when (section) {
        AgendaSection.TODAY -> "Today"
        AgendaSection.TOMORROW -> "Tomorrow"
        AgendaSection.THIS_WEEK -> "This week"
        AgendaSection.LATER -> "Later"
        AgendaSection.UNSCHEDULED -> "Unscheduled"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (section == AgendaSection.UNSCHEDULED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (section == AgendaSection.UNSCHEDULED) {
        Text(
            text = "Interested leads without a scheduled re-call. Older first — these are most at risk of going cold.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun FollowUpRow(followUp: FollowUp, onClick: () -> Unit) {
    val timeLabel = remember(followUp.scheduledAt) {
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        formatter.format(followUp.scheduledAt.atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = followUp.clientName ?: "Client",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!followUp.clientPhone.isNullOrBlank()) {
                    Text(
                        text = followUp.clientPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (followUp.reason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = followUp.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Card variant for orphan INTERESTED clients (no scheduled follow-up).
 * Same dimensions as a regular follow-up row, with a softer surface
 * so the agent reads it as "different status, not a calendar event".
 */
@Composable
private fun UnscheduledRow(
    client: Client,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = client.name, size = 44.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = client.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                client.lastNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\"${note.take(80)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = client.lastCalledAt?.let { "Last ${formatRelative(it)}" } ?: "Never called",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Descartar cliente") },
                        onClick = {
                            menuOpen = false
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val days = ChronoUnit.DAYS.between(instant, now).coerceAtLeast(0)
    val hours = ChronoUnit.HOURS.between(instant, now).coerceAtLeast(0)
    return when {
        days >= 7 -> "${days / 7}w ago"
        days >= 1 -> "${days}d ago"
        hours >= 1 -> "${hours}h ago"
        else -> "just now"
    }
}

