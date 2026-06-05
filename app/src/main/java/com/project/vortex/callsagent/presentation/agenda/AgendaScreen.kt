package com.project.vortex.callsagent.presentation.agenda

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.presentation.clients.components.DismissClientSheet
import com.project.vortex.callsagent.presentation.common.relativeFuture
import com.project.vortex.callsagent.presentation.common.relativePast
import com.project.vortex.callsagent.ui.components.InterestLevelChip
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.theme.PillShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Internal list-pane composable for the Agenda tab.
 *
 * Previously the public `AgendaScreen`. Renamed when the screen was
 * split into adaptive list/detail panes for tablets — the public
 * entry point now lives in [AgendaScreenAdaptive], which picks
 * between this list pane on its own (compact widths) or list+detail
 * with a draggable divider (wide widths).
 *
 * Kept intentionally unchanged: the entire pre-existing UI (sections,
 * scheduled/unscheduled rows, dismissal sheet, scroll-to-top tick) is
 * preserved bit-for-bit on phones and reused as the list pane on
 * tablets.
 */
@Composable
internal fun AgendaListPane(
    onFollowUpSelected: (String) -> Unit,
    /**
     * Increments every time the agent re-taps the Agenda tab on the
     * bottom nav while already on it. Each new value triggers a
     * scroll-to-top of the list.
     */
    scrollToTopTick: Int = 0,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val agenda by viewModel.agenda.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var dismissTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

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
                EmptyAgenda(isRefreshing = uiState.isRefreshing)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    agenda.forEach { (section, items) ->
                        item(key = "header-${section.name}") {
                            SectionHeader(section, items.size)
                        }
                        itemsIndexed(items, key = { _, it -> it.stableKey() }) { index, entry ->
                            when (entry) {
                                is AgendaItem.Scheduled -> ScheduledRow(
                                    followUp = entry.followUp,
                                    interestLevel = entry.interestLevel,
                                    isToday = section == AgendaSection.TODAY,
                                    onClick = { onFollowUpSelected(entry.followUp.clientId) },
                                )
                                is AgendaItem.Unscheduled -> UnscheduledRow(
                                    client = entry.client,
                                    onClick = { onFollowUpSelected(entry.client.id) },
                                    onDismiss = {
                                        dismissTarget = entry.client.id to entry.client.name
                                    },
                                )
                            }
                            if (index < items.lastIndex) RowDivider()
                        }
                    }
                    item("bottom_spacer") { Spacer(Modifier.height(24.dp)) }
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

// ─────────────────────────────────────────────────────────────────────────────
// Headers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(section: AgendaSection, count: Int) {
    val isToday = section == AgendaSection.TODAY
    val label = when (section) {
        AgendaSection.TODAY -> stringResource(R.string.agenda_section_today)
        AgendaSection.TOMORROW -> stringResource(R.string.agenda_section_tomorrow)
        AgendaSection.THIS_WEEK -> stringResource(R.string.agenda_section_this_week)
        AgendaSection.LATER -> stringResource(R.string.agenda_section_later)
        AgendaSection.UNSCHEDULED -> stringResource(R.string.agenda_section_unscheduled)
    }
    val isUnscheduled = section == AgendaSection.UNSCHEDULED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = if (isToday) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    isUnscheduled -> MaterialTheme.colorScheme.onSurfaceVariant
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        if (isUnscheduled) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.agenda_unscheduled_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scheduled row (Gmail-style)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduledRow(
    followUp: FollowUp,
    interestLevel: InterestLevel?,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    // Business clock — see BusinessConfig. Without this, agents in
    // Caracas would see follow-ups in their local wall-clock, off by
    // one hour from the admin/client view.
    val zone = BusinessConfig.BUSINESS_TIMEZONE
    val now = Instant.now()
    val isOverdue = followUp.scheduledAt.isBefore(now)

    val absoluteTime = remember(followUp.scheduledAt, isToday) {
        val pattern = if (isToday) "h:mm a" else "EEE · h:mm a"
        DateTimeFormatter.ofPattern(pattern).format(followUp.scheduledAt.atZone(zone))
    }
    val relativeTime = relativeFuture(followUp.scheduledAt, now)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val clientFallback = stringResource(R.string.agenda_client_fallback)
        Avatar(name = followUp.clientName ?: clientFallback, size = 44.dp)
        Spacer(Modifier.width(12.dp))

        // Identity column
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = followUp.clientName ?: clientFallback,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!followUp.clientPhone.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = followUp.clientPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (interestLevel != null) {
                        Spacer(Modifier.width(8.dp))
                        InterestLevelChip(level = interestLevel)
                    }
                }
            } else if (interestLevel != null) {
                Spacer(Modifier.height(2.dp))
                InterestLevelChip(level = interestLevel)
            }
            // Optional reason — older follow-ups still carry it.
            if (followUp.reason.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "\"${followUp.reason}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Time column (right-aligned)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = absoluteTime,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOverdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            if (isOverdue) {
                OverdueBadge()
            } else {
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverdueBadge() {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.agenda_overdue),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Unscheduled row (orphan INTERESTED — no follow-up)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnscheduledRow(
    client: Client,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(name = client.name, size = 40.dp)
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = client.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                client.interestLevel?.let { level ->
                    Spacer(Modifier.width(8.dp))
                    InterestLevelChip(level = level)
                }
            }
            client.lastNote?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "\"${note}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = client.lastCalledAt?.let { relativePast(it) }
                    ?: stringResource(R.string.agenda_never_called),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.agenda_attempts,
                        client.callAttempts,
                        client.callAttempts,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.agenda_more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.agenda_dismiss_client)) },
                    onClick = {
                        menuOpen = false
                        onDismiss()
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp), // align with text after avatar
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun EmptyAgenda(isRefreshing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isRefreshing) stringResource(R.string.agenda_loading)
            else stringResource(R.string.agenda_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("unused")
private fun unusedColorTouch(): Color = Color.Transparent

// LazyListScope.itemsIndexed shim — not auto-imported in this module yet.
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    noinline key: (index: Int, item: T) -> Any,
    crossinline itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(index: Int, item: T) -> Unit,
) = items(
    count = items.size,
    key = { idx -> key(idx, items[idx]) },
    itemContent = { idx -> itemContent(idx, items[idx]) },
)
