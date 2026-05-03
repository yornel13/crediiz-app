package com.project.vortex.callsagent.presentation.precall

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.presentation.autocall.AutoCallSession
import com.project.vortex.callsagent.presentation.clients.components.DismissClientSheet
import com.project.vortex.callsagent.presentation.autocall.PendingAutoCall
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.components.SectionHeader
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.Teal700
import com.project.vortex.callsagent.ui.theme.Teal900
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val HeroShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCallScreen(
    onBack: () -> Unit,
    onSkipToNext: (clientId: String) -> Unit,
    onSkipToSummary: () -> Unit,
    onExitAutoCall: () -> Unit,
    viewModel: PreCallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val nextFollowUp by viewModel.nextFollowUp.collectAsState()
    val autoCallSession by viewModel.autoCallSession.collectAsState()
    val pendingAutoCall by viewModel.pendingAutoCall.collectAsState()
    val autoCallDelaySeconds by viewModel.autoCallDelaySeconds.collectAsState()

    var dismissSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PreCallEvent.SkipToNext -> onSkipToNext(event.clientId)
                PreCallEvent.SkipToSummary -> onSkipToSummary()
                PreCallEvent.ExitAutoCall -> onExitAutoCall()
                PreCallEvent.Dismissed -> onBack()
            }
        }
    }

    // Back during an auto-call session also clears the session — keeps the
    // orchestrator state honest (no ghost session badge if the agent
    // navigates back to a non-queue client later).
    val effectiveOnBack: () -> Unit = {
        if (autoCallSession != null) viewModel.exitAutoCall()
        else onBack()
    }

    Scaffold(
        bottomBar = {
            CallActionBar(
                client = uiState.client,
                inAutoCall = autoCallSession != null,
                onCall = viewModel::startCall,
                onSkip = viewModel::skipCurrent,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier
                .fillMaxSize()
                .padding(padding))

            uiState.client == null -> ErrorState(
                message = uiState.errorMessage ?: "Client not found",
                onBack = effectiveOnBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> PreCallContent(
                client = uiState.client!!,
                notes = notes,
                nextFollowUp = nextFollowUp,
                autoCallSession = autoCallSession,
                onAddNote = viewModel::openNoteSheet,
                onBack = effectiveOnBack,
                onExitAutoCall = viewModel::exitAutoCall,
                onRequestDismiss = { dismissSheetOpen = true },
                contentPadding = padding,
            )
        }

        if (uiState.isNoteSheetOpen) {
            AddNoteSheet(
                isSubmitting = uiState.isSubmittingNote,
                onDismiss = viewModel::dismissNoteSheet,
                onSave = viewModel::saveManualNote,
            )
        }

        if (dismissSheetOpen) {
            DismissClientSheet(
                clientName = uiState.client?.name.orEmpty(),
                onDismiss = { dismissSheetOpen = false },
                onConfirm = { code, free ->
                    viewModel.dismissClient(code, free)
                    dismissSheetOpen = false
                },
            )
        }

        // Auto-call countdown — full-screen overlay above everything else.
        // Renders only when (a) a pending auto-call exists, (b) the client
        // for it matches the one this PreCall is showing (avoids ghost
        // overlays during navigation), and (c) the session is still active.
        // When the agent configured the countdown to 0 we skip the overlay
        // entirely and fire the call immediately — same effect, no flash
        // of the "0" frame the overlay would otherwise render for 1 s.
        pendingAutoCall?.let { pending ->
            val client = uiState.client
            val sessionActive = autoCallSession != null
            if (sessionActive && client != null && pending.clientId == client.id) {
                if (autoCallDelaySeconds <= 0) {
                    LaunchedEffect(pending.clientId) {
                        viewModel.onAutoCallCountdownComplete()
                    }
                } else {
                    AutoCallCountdownOverlay(
                        clientName = client.name,
                        delaySeconds = autoCallDelaySeconds,
                        onComplete = viewModel::onAutoCallCountdownComplete,
                        onCancel = viewModel::cancelAutoCall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreCallContent(
    client: Client,
    notes: List<Note>,
    nextFollowUp: FollowUp?,
    autoCallSession: AutoCallSession?,
    onAddNote: () -> Unit,
    onBack: () -> Unit,
    onExitAutoCall: () -> Unit,
    onRequestDismiss: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("hero") {
            Hero(
                client = client,
                autoCallSession = autoCallSession,
                onBack = onBack,
                onExitAutoCall = onExitAutoCall,
                onRequestDismiss = onRequestDismiss,
            )
        }

        // ─── Scheduled follow-up reminder ──────────────────────────────
        // If this client has a future pending follow-up, surface it
        // right under the hero so the agent sees the commitment they
        // (or another agent) made.
        nextFollowUp?.let { followUp ->
            item("followup_reminder") {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ScheduledCallCard(followUp = followUp)
                }
            }
        }

        // ─── Personal data (cedula / SS / salary) ──────────────────────
        // Only render the section if at least one of the three is set.
        // Excel uploads sometimes omit cedula or ssNumber; salary may
        // be null when the source data is missing it.
        if (client.cedula != null || client.ssNumber != null || client.salary != null) {
            item("personal_header") {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionHeader(title = "Personal data")
                }
            }
            item("personal") {
                PersonalDataCard(client = client)
            }
        }

        if (client.extraData.isNotEmpty()) {
            item("extra_header") {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionHeader(title = "Details")
                }
            }
            item("extra") {
                ExtraDataCard(client = client)
            }
        }

        if (!client.lastNote.isNullOrBlank()) {
            item("lastnote_header") {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionHeader(title = "Last note")
                }
            }
            item("lastnote") {
                LastNoteCard(text = client.lastNote)
            }
        }

        item("notes_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            ) {
                SectionHeader(
                    title = "Notes",
                    count = notes.size,
                    trailing = {
                        OutlinedButton(
                            onClick = onAddNote,
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Add note",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )
            }
        }

        if (notes.isEmpty()) {
            item("notes_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { EmptyNotes() }
            }
        } else {
            items(notes, key = { it.mobileSyncId }) { note ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    TimelineNoteRow(note = note)
                }
            }
        }
    }
}

/**
 * Compact gradient hero containing avatar + identity + status, plus an
 * embedded stats row at the bottom (3 metrics on a translucent surface).
 * No floating-card overlap — clean LazyColumn flow, no hit-testing bugs.
 */
@Composable
private fun Hero(
    client: Client,
    autoCallSession: AutoCallSession?,
    onBack: () -> Unit,
    onExitAutoCall: () -> Unit,
    onRequestDismiss: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.let {
        0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue < 0.5f
    }
    val gradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Teal900, Teal900.copy(alpha = 0.7f))
        } else {
            listOf(Teal700, Teal900)
        },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(gradient),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                if (autoCallSession != null) {
                    AutoCallTopChip(
                        position = autoCallSession.displayPosition,
                        total = autoCallSession.total,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Auto-call exit lives only on the Back button now
                // (it auto-exits the session on Back). This slot is
                // now the per-client overflow menu — only "Dismiss
                // client" today, can grow in the future.
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More actions",
                        tint = Color.White,
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Dismiss client") },
                            onClick = {
                                menuOpen = false
                                onRequestDismiss()
                            },
                        )
                    }
                }
            }

            // Identity block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(name = client.name, size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = client.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(10.dp))
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
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = client.status.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Embedded stats row — translucent white surface, sits inside the hero.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(vertical = 14.dp),
            ) {
                HeroStatCell(
                    label = "Attempts",
                    value = client.callAttempts.toString(),
                    modifier = Modifier.weight(1f),
                )
                HeroDivider()
                HeroStatCell(
                    label = "Last call",
                    value = client.lastCalledAt?.let(::formatRelativeShort) ?: "Never",
                    modifier = Modifier.weight(1f),
                )
                HeroDivider()
                HeroStatCell(
                    label = "Outcome",
                    value = client.lastOutcome?.let(::outcomeLabel) ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }

            if (autoCallSession != null) {
                Spacer(Modifier.height(20.dp))
                HeroProgressBar(
                    position = autoCallSession.displayPosition,
                    total = autoCallSession.total,
                )
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun HeroStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .heightIn(min = 32.dp)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

@Composable
private fun ScheduledCallCard(followUp: FollowUp) {
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val isOverdue = followUp.scheduledAt.isBefore(now)
    val scheduledLocal = remember(followUp.scheduledAt) {
        followUp.scheduledAt.atZone(zone)
    }
    val absoluteText = remember(scheduledLocal, isOverdue) {
        val today = LocalDate.now(zone)
        val date = scheduledLocal.toLocalDate()
        val dayLabel = when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date == today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern("EEE, MMM d").format(scheduledLocal)
        }
        val timeLabel = DateTimeFormatter.ofPattern("h:mm a").format(scheduledLocal)
        "$dayLabel · $timeLabel"
    }
    val relative = remember(followUp.scheduledAt, now, isOverdue) {
        if (isOverdue) formatOverdueRelative(followUp.scheduledAt, now)
        else formatUpcomingRelative(followUp.scheduledAt, now)
    }

    val container = if (isOverdue) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    val onContainer = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onTertiaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(onContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isOverdue) "Scheduled call (overdue)" else "Scheduled call",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer.copy(alpha = 0.85f),
                )
                Text(
                    text = absoluteText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Text(
                    text = relative,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

private fun formatUpcomingRelative(target: Instant, now: Instant): String {
    val minutes = ChronoUnit.MINUTES.between(now, target).coerceAtLeast(0)
    return when {
        minutes < 1 -> "starting now"
        minutes < 60 -> "in ${minutes}m"
        minutes < 60 * 24 -> "in ${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "in ${minutes / (60 * 24)}d"
        else -> "in ${minutes / (60 * 24 * 7)}w"
    }
}

private fun formatOverdueRelative(target: Instant, now: Instant): String {
    val minutes = ChronoUnit.MINUTES.between(target, now).coerceAtLeast(0)
    return when {
        minutes < 60 -> "${minutes}m late"
        minutes < 60 * 24 -> "${minutes / 60}h late"
        else -> "${minutes / (60 * 24)}d late"
    }
}

@Composable
private fun PersonalDataCard(client: Client) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val rows = buildList {
                client.cedula?.takeIf { it.isNotBlank() }?.let { add("Cédula" to it) }
                client.ssNumber?.takeIf { it.isNotBlank() }?.let { add("Social Security" to it) }
                client.salary?.let { add("Salary" to formatSalary(it)) }
            }
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun formatSalary(amount: Double): String {
    // Whole-number USD when possible (most seeded values are integers)
    // — falls back to two decimals otherwise.
    val whole = amount.toLong()
    return if (amount == whole.toDouble()) "$%,d".format(whole)
    else "$%,.2f".format(amount)
}

@Composable
private fun ExtraDataCard(client: Client) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            client.extraData.entries.filter { it.value != null }.forEachIndexed { index, entry ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entry.key.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastNoteCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TimelineNoteRow(note: Note) {
    val palette = note.type.palette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = palette.onContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(shape = PillShape, color = palette.container) {
                        Text(
                            text = note.type.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTimestamp(note.deviceCreatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EmptyNotes() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No notes yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Use \"Add note\" to write something about this client.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallActionBar(
    client: Client?,
    inAutoCall: Boolean,
    onCall: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (inAutoCall) {
                OutlinedButton(
                    onClick = onSkip,
                    enabled = client != null,
                    modifier = Modifier.heightIn(min = 60.dp),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Button(
                onClick = onCall,
                enabled = client != null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneGreen,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Call ${client?.phone.orEmpty()}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteSheet(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Add a note",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Notes are append-only and visible to you and the admin panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What would you like to remember about this client?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(text) },
                    enabled = !isSubmitting && text.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) {
                Text("Go back")
            }
        }
    }
}

private fun outcomeLabel(outcome: CallOutcome): String = when (outcome) {
    CallOutcome.INTERESTED -> "Interested"
    CallOutcome.NOT_INTERESTED -> "Not int."
    CallOutcome.NO_ANSWER -> "No ans."
    CallOutcome.BUSY -> "Busy"
    CallOutcome.INVALID_NUMBER -> "Invalid"
}

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d · h:mm a")

private fun formatTimestamp(instant: Instant): String =
    timestampFormatter.format(instant.atZone(ZoneId.systemDefault()))

private fun formatRelativeShort(instant: Instant): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        else -> {
            val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            "${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}"
        }
    }
}

// ─── Auto-call hero chip + progress + countdown overlay (Phase 4) ────────

/**
 * The "Auto-call · 3 / 116" pill that sits in the hero's top bar next to
 * the back arrow. White-translucent on the gradient — same vocabulary as
 * the status pill below the avatar.
 */
@Composable
private fun AutoCallTopChip(
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Auto-call",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$position / $total",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Thin progress track sitting at the bottom of the hero, just inside the
 * rounded corners. Visualizes how far the agent has advanced through the
 * auto-call queue. Pure decoration — does NOT itself accept input.
 */
@Composable
private fun HeroProgressBar(position: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (position.toFloat() / total).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White),
        )
    }
}

@Composable
private fun AutoCallCountdownOverlay(
    clientName: String,
    delaySeconds: Int,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val total = delaySeconds.coerceAtLeast(1)
    var remaining by remember(total) { mutableIntStateOf(total) }

    LaunchedEffect(total) {
        repeat(total) {
            kotlinx.coroutines.delay(1000)
            remaining -= 1
        }
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Calling next in",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = remaining.coerceAtLeast(0).toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = clientName.ifBlank { "Next client" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
