package com.project.vortex.callsagent.presentation.clients

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.common.enums.MissedCallReason
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.MissedCall
import com.project.vortex.callsagent.presentation.clients.components.AddNoteSheet
import com.project.vortex.callsagent.presentation.clients.components.ClientsViewSelector
import com.project.vortex.callsagent.presentation.clients.components.InterestedClientCard
import com.project.vortex.callsagent.presentation.clients.components.RecentClientCard
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.Amber600
import com.project.vortex.callsagent.ui.theme.Emerald600
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.Rose600
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    onClientSelected: (String) -> Unit,
    onStartAutoCall: (firstClientId: String) -> Unit,
    viewModel: ClientsViewModel = hiltViewModel(),
) {
    val clients by viewModel.clients.collectAsState()
    val totalPending by viewModel.totalPendingCount.collectAsState()
    val totalRecent by viewModel.totalRecentCount.collectAsState()
    val totalInterested by viewModel.totalInterestedCount.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val viewKind by viewModel.viewKind.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val missedCalls by viewModel.missedCalls.collectAsState()
    val syncIndicator by viewModel.syncIndicator.collectAsState()
    var missedSheetOpen by remember { mutableStateOf(false) }
    // Holds the clientId currently being annotated via the AddNote sheet.
    var noteSheetForClient by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        floatingActionButton = {
            // Auto-call only makes sense over the Pendientes queue. Hiding
            // the FAB on Recientes / Interesados avoids ambiguity about
            // what set is being dialed.
            if (clients.isNotEmpty() && viewKind == ClientsViewKind.PENDIENTES) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val firstClientId = viewModel.startAutoCall()
                        if (firstClientId != null) onStartAutoCall(firstClientId)
                    },
                    icon = { Icon(Icons.Filled.PhoneInTalk, contentDescription = null) },
                    text = { Text("Auto-call", fontWeight = FontWeight.SemiBold) },
                    containerColor = PhoneGreen,
                    contentColor = Color.White,
                )
            }
        },
        // HomeScreen's outer Scaffold already consumes the system insets;
        // skipping them here avoids double-padding the top/bottom of the screen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("hero") {
                Hero(
                    activeView = viewKind,
                    pendingCount = totalPending,
                    recentCount = totalRecent,
                    interestedCount = totalInterested,
                    syncState = syncIndicator,
                    onForceSync = viewModel::forceSyncNow,
                )
            }

            if (missedCalls.isNotEmpty()) {
                item("missed_banner") {
                    MissedCallsBanner(
                        count = missedCalls.size,
                        onClick = { missedSheetOpen = true },
                    )
                }
            }

            item("search") {
                SearchField(query = query, onQueryChange = viewModel::onSearchQueryChange)
            }

            item("view_selector") {
                ClientsViewSelector(
                    selected = viewKind,
                    pendingCount = totalPending,
                    recentCount = totalRecent,
                    interestedCount = totalInterested,
                    onSelected = viewModel::onViewKindChange,
                )
            }

            // Show "X of Y" hint only while a search is active. The
            // total-of count maps to the active view to keep the math
            // honest.
            if (query.isNotBlank()) {
                item("search_summary") {
                    SearchResultSummary(
                        matchCount = clients.size,
                        totalCount = when (viewKind) {
                            ClientsViewKind.PENDIENTES -> totalPending
                            ClientsViewKind.RECIENTES -> totalRecent
                            ClientsViewKind.INTERESADOS -> totalInterested
                        },
                    )
                }
            }

            uiState.errorMessage?.let { msg ->
                item("error") { ErrorBanner(message = msg) }
            }

            if (clients.isEmpty()) {
                item("empty") {
                    EmptyState(
                        viewKind = viewKind,
                        isLoading = uiState.isRefreshing,
                        isSearching = query.isNotBlank(),
                    )
                }
            } else {
                items(clients, key = { "${viewKind.name}_${it.id}" }) { client ->
                    when (viewKind) {
                        ClientsViewKind.PENDIENTES -> ClientCard(
                            client = client,
                            onClick = { onClientSelected(client.id) },
                        )
                        ClientsViewKind.RECIENTES -> RecentClientCard(
                            client = client,
                            onOpen = { onClientSelected(client.id) },
                            onAddNote = { noteSheetForClient = client.id to client.name },
                            onCallAgain = { onClientSelected(client.id) },
                        )
                        ClientsViewKind.INTERESADOS -> InterestedClientCard(
                            client = client,
                            onOpen = { onClientSelected(client.id) },
                            onAddNote = { noteSheetForClient = client.id to client.name },
                            onCall = { onClientSelected(client.id) },
                        )
                    }
                }
            }
        }

        noteSheetForClient?.let { (clientId, clientName) ->
            AddNoteSheet(
                clientName = clientName,
                onDismiss = { noteSheetForClient = null },
                onSave = { text ->
                    viewModel.addManualNote(clientId, text)
                    noteSheetForClient = null
                },
            )
        }

        if (missedSheetOpen) {
            MissedCallsSheet(
                missedCalls = missedCalls,
                onDismiss = { missedSheetOpen = false },
                onCallBack = { missed ->
                    val matched = missed.matchedClientId
                    if (matched != null) {
                        onClientSelected(matched)
                        viewModel.acknowledgeMissedCall(missed.id)
                        missedSheetOpen = false
                    } else {
                        // Number not in queue — acknowledge so it stops nagging.
                        // A future enhancement could prompt to dial anyway.
                        viewModel.acknowledgeMissedCall(missed.id)
                    }
                },
                onDismissEntry = viewModel::acknowledgeMissedCall,
                onDismissAll = {
                    viewModel.acknowledgeAllMissedCalls()
                    missedSheetOpen = false
                },
            )
        }
    }
}

@Composable
private fun Hero(
    activeView: ClientsViewKind,
    pendingCount: Int,
    recentCount: Int,
    interestedCount: Int,
    syncState: SyncIndicatorState,
    onForceSync: () -> Unit,
) {
    val (headlineCount, headlineUnit) = when (activeView) {
        ClientsViewKind.PENDIENTES ->
            pendingCount to (if (pendingCount == 1) "client to call" else "clients to call")
        ClientsViewKind.RECIENTES ->
            recentCount to (if (recentCount == 1) "recent call" else "recent calls")
        ClientsViewKind.INTERESADOS ->
            interestedCount to (if (interestedCount == 1) "interested lead" else "interested leads")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your queue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            SyncChip(state = syncState, onClick = onForceSync)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = headlineCount.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = headlineUnit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * Compact sync-status chip in the top-right of the Clients hero. Color
 * + label come from [SyncIndicatorState]. Tapping triggers an immediate
 * sync (covers the offline-then-online case where the agent doesn't
 * want to wait for the periodic worker).
 */
@Composable
private fun SyncChip(state: SyncIndicatorState, onClick: () -> Unit) {
    val (icon, label, color) = when (state) {
        SyncIndicatorState.Syncing ->
            Triple(Icons.Filled.Sync, "Syncing…", MaterialTheme.colorScheme.primary)
        SyncIndicatorState.AllSynced ->
            Triple(Icons.Filled.CheckCircle, "Synced", Emerald600)
        is SyncIndicatorState.Pending ->
            Triple(Icons.Filled.Sync, "${state.count} pending", Amber600)
        is SyncIndicatorState.Failed ->
            Triple(Icons.Filled.CloudOff, "${state.pendingCount} unsynced", Rose600)
    }

    val container = color.copy(alpha = 0.12f)

    Surface(
        shape = PillShape,
        color = container,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search by name or phone") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ClientCard(client: Client, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Meta row — attempts + last call + status pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AttemptsChip(attempts = client.callAttempts)
                    if (client.lastCalledAt != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· ${formatRelative(client.lastCalledAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusPill(
                    label = client.status.label(),
                    palette = client.status.palette(),
                )
            }
        }
    }
}

@Composable
private fun AttemptsChip(attempts: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (attempts == 0) "No attempts" else "$attempts ${if (attempts == 1) "attempt" else "attempts"}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultSummary(matchCount: Int, totalCount: Int) {
    Text(
        text = "Showing $matchCount of $totalCount",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EmptyState(
    viewKind: ClientsViewKind,
    isLoading: Boolean,
    isSearching: Boolean,
) {
    val (title, subtitle, icon) = when {
        isLoading -> Triple("Loading clients...", "Hang on a moment", Icons.Filled.Phone)
        isSearching -> Triple(
            "No matches found",
            "Try a different name or phone.",
            Icons.Filled.Search,
        )
        else -> when (viewKind) {
            ClientsViewKind.PENDIENTES -> Triple(
                "No clients assigned yet",
                "Pull down to refresh",
                Icons.Filled.Phone,
            )
            ClientsViewKind.RECIENTES -> Triple(
                "No recent calls",
                "Calls you make in the last 24 h will show up here.",
                Icons.Filled.Phone,
            )
            ClientsViewKind.INTERESADOS -> Triple(
                "No interested leads yet",
                "Mark INTERESTED in Post-Call to see leads here.",
                Icons.Filled.CheckCircle,
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Missed-call banner + sheet (Phase 3.5) ─────────────────────────────

@Composable
private fun MissedCallsBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PhoneMissed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count missed ${if (count == 1) "call" else "calls"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Tap to review and call back",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissedCallsSheet(
    missedCalls: List<MissedCall>,
    onDismiss: () -> Unit,
    onCallBack: (MissedCall) -> Unit,
    onDismissEntry: (id: String) -> Unit,
    onDismissAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Missed calls",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (missedCalls.size > 1) {
                    TextButton(onClick = onDismissAll) {
                        Text("Dismiss all")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Calls that came in while you were unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            missedCalls.forEach { missed ->
                MissedCallRow(
                    missed = missed,
                    onCallBack = { onCallBack(missed) },
                    onDismiss = { onDismissEntry(missed.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MissedCallRow(
    missed: MissedCall,
    onCallBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = missed.matchedClientId?.let { "Assigned client" } ?: "Unknown number"
    val canCallBack = missed.matchedClientId != null
    val reasonLabel = when (missed.reason) {
        MissedCallReason.REJECTED -> "Rejected"
        MissedCallReason.NOT_ANSWERED -> "Missed"
        MissedCallReason.BUSY_OTHER_CALL -> "Busy"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = missed.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = reasonLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatRelative(missed.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canCallBack) {
                OutlinedButton(
                    onClick = onCallBack,
                    shape = PillShape,
                ) {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Call back", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> {
            val date = instant.atZone(zone).toLocalDate()
            "${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}"
        }
    }
}
