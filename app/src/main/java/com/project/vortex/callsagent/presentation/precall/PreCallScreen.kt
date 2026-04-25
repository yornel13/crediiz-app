package com.project.vortex.callsagent.presentation.precall

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.Note
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCallScreen(
    onBack: () -> Unit,
    viewModel: PreCallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.notes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.client?.name ?: "Client") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            CallActionBar(client = uiState.client)
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier
                .fillMaxSize()
                .padding(padding))

            uiState.client == null -> ErrorState(
                message = uiState.errorMessage ?: "Client not found",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> PreCallContent(
                client = uiState.client!!,
                notes = notes,
                onAddNote = viewModel::openNoteSheet,
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

        uiState.errorMessage?.let { msg ->
            // Error message currently surfaced inline only. A snackbar host
            // could be added here; deferring until we have a global pattern.
            LaunchedEffect(msg) { /* no-op placeholder */ }
        }
    }
}

@Composable
private fun PreCallContent(
    client: Client,
    notes: List<Note>,
    onAddNote: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("client_card") { ClientInfoCard(client) }
        item("history_card") { CallHistorySummary(client) }
        item("notes_header") {
            NotesSectionHeader(count = notes.size, onAddNote = onAddNote)
        }
        if (notes.isEmpty()) {
            item("notes_empty") { EmptyNotes() }
        } else {
            items(notes, key = { it.mobileSyncId }) { note ->
                NoteRow(note = note)
            }
        }
    }
}

@Composable
private fun ClientInfoCard(client: Client) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = client.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = client.phone,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            StatusBadge(status = client.status.name)

            if (client.extraData.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                client.extraData.forEach { (key, value) ->
                    if (value != null) {
                        ExtraDataRow(key = key, value = value.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ExtraDataRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = key.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CallHistorySummary(client: Client) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Call history",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            HistoryRow(label = "Attempts", value = client.callAttempts.toString())
            HistoryRow(
                label = "Last call",
                value = client.lastCalledAt?.let(::formatRelativeTimestamp) ?: "Never",
            )
            HistoryRow(
                label = "Last outcome",
                value = client.lastOutcome?.let(::outcomeLabel) ?: "—",
            )

            if (!client.lastNote.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last note",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = client.lastNote,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NotesSectionHeader(count: Int, onAddNote: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 0) "Notes" else "Notes ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = onAddNote) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(text = "Add note", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun EmptyNotes() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No notes yet for this client.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteRow(note: Note) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteTypeBadge(type = note.type)
                Text(
                    text = formatRelativeTimestamp(note.deviceCreatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = note.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoteTypeBadge(type: NoteType) {
    val (label, color) = when (type) {
        NoteType.CALL -> "During call" to MaterialTheme.colorScheme.tertiaryContainer
        NoteType.POST_CALL -> "Post-call" to MaterialTheme.colorScheme.secondaryContainer
        NoteType.MANUAL -> "Manual" to MaterialTheme.colorScheme.surfaceVariant
        NoteType.FOLLOW_UP -> "Follow-up" to MaterialTheme.colorScheme.primaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CallActionBar(client: Client?) {
    val context = LocalContext.current
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Button(
                onClick = {
                    val phone = client?.phone ?: return@Button
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    runCatching { context.startActivity(intent) }
                },
                enabled = client != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = "Call ${client?.phone ?: ""}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Add a note",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Notes are append-only and visible to you and the admin panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What would you like to remember about this client?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    enabled = !isSubmitting,
                ) { Text("Cancel") }
                Spacer(Modifier.height(0.dp))
                Button(
                    onClick = { onSave(text) },
                    enabled = !isSubmitting && text.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save")
                }
            }
            Spacer(Modifier.height(16.dp))
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
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun outcomeLabel(outcome: CallOutcome): String = when (outcome) {
    CallOutcome.INTERESTED -> "Interested"
    CallOutcome.NOT_INTERESTED -> "Not interested"
    CallOutcome.NO_ANSWER -> "No answer"
    CallOutcome.BUSY -> "Busy"
    CallOutcome.INVALID_NUMBER -> "Invalid number"
}

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun formatRelativeTimestamp(instant: Instant): String =
    timestampFormatter.format(instant.atZone(ZoneId.systemDefault()))
