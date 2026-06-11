package com.project.vortex.callsagent.presentation.postcall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.presentation.precall.PreCallReadOnlyPanel
import com.project.vortex.callsagent.domain.model.Interaction
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.theme.Teal700
import com.project.vortex.callsagent.ui.theme.Teal900
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.icon
import com.project.vortex.callsagent.ui.theme.isAnswered
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Built per-call with the process locale so weekday/month names and
// AM/PM follow the language chosen at runtime (Activities are recreated
// on language change — see ui/locale/LocaleContext).
private fun dateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.getDefault())

private fun timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSavedNextInSession: (clientId: String) -> Unit,
    onSavedSessionComplete: () -> Unit,
    viewModel: PostCallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PostCallEvent.Saved -> onSaved()
                is PostCallEvent.SavedNextInSession ->
                    onSavedNextInSession(event.clientId)
                PostCallEvent.SavedSessionComplete -> onSavedSessionComplete()
            }
        }
    }

    // No topBar / bottomBar at the Scaffold level — every visual
    // element (green hero, form, Save) lives INSIDE the left panel.
    // This way the Save and the hero scale with the left panel width
    // (45% in split, 100% in compact) instead of spanning the full
    // screen as a giant bar, and the two-panel structure of PostCall
    // mirrors InCallScreen exactly: hero strip at top, action body
    // in the middle, primary CTA at the bottom — all bounded to the
    // left panel.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.client == null || state.interaction == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.errorMessage
                        ?: stringResource(R.string.postcall_load_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            else -> {
                // Visual armonization with InCallScreen: same 45/55
                // split with the call-action content on the left and
                // the read-only client info pane on the right. The
                // proportions mirror InCallScreen exactly so the two
                // screens look like siblings — same skeleton, just
                // different action area in the left panel.
                val showSplit = WindowSize.isWideWidth && !WindowSize.isCompactHeight
                if (showSplit) {
                    Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                        PostCallLeftPanel(
                            state = state,
                            onBack = onBack,
                            onSelectOutcome = viewModel::selectOutcome,
                            onNoteChange = viewModel::onNoteChange,
                            onDateChange = viewModel::onFollowUpDateChange,
                            onTimeChange = viewModel::onFollowUpTimeChange,
                            onSave = viewModel::save,
                            modifier = Modifier.weight(0.45f).fillMaxHeight(),
                        )
                        PreCallReadOnlyPanel(
                            clientId = state.client!!.id,
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    PostCallLeftPanel(
                        state = state,
                        onBack = onBack,
                        onSelectOutcome = viewModel::selectOutcome,
                        onNoteChange = viewModel::onNoteChange,
                        onDateChange = viewModel::onFollowUpDateChange,
                        onTimeChange = viewModel::onFollowUpTimeChange,
                        onSave = viewModel::save,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }
}

/**
 * Self-contained left-panel of PostCall — green hero strip at top,
 * scrollable outcome form in the middle, Save button pinned at the
 * bottom. **All within this composable's bounds**, so the same
 * structure works whether the panel takes 45% of the screen
 * (split mode) or 100% (compact mode) — no global TopBar/BottomBar
 * spanning the whole screen.
 *
 * Mirrors the InCallScreen `CallControlPanel` precept:
 *   ┌────────────────┐
 *   │ Hero (green)   │   <- identifies the client and call state
 *   │                │
 *   ├────────────────┤
 *   │                │
 *   │ Action body    │   <- LiveNote+controls (InCall) /
 *   │ (scrollable)   │      outcome form     (PostCall)
 *   │                │
 *   ├────────────────┤
 *   │ Primary CTA    │   <- End call (InCall) / Save (PostCall)
 *   └────────────────┘
 */
@Composable
private fun PostCallLeftPanel(
    state: PostCallUiState,
    onBack: () -> Unit,
    onSelectOutcome: (CallOutcome) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            // `imePadding()` shrinks this column from the bottom by
            // the IME height whenever the soft keyboard is up. The
            // form's LazyColumn (`weight(1f)`) absorbs the loss and
            // becomes scrollable inside its reduced space, so the
            // Save button at the bottom STAYS visible above the
            // keyboard. Without this, the agent typing the note
            // would lose access to the Save button.
            .imePadding(),
    ) {
        // Top: green hero strip — same visual treatment as the
        // InCallScreen status hero, just with the "Call ended" pill
        // showing call duration.
        CallEndedHero(
            client = state.client,
            durationSeconds = state.interaction?.durationSeconds ?: 0,
            onBack = onBack,
        )
        // Middle: scrollable form. `weight(1f)` makes it fill the
        // remaining vertical space between the hero (top) and the
        // SaveBar (bottom).
        PostCallContent(
            state = state,
            client = state.client!!,
            interaction = state.interaction!!,
            onSelectOutcome = onSelectOutcome,
            onNoteChange = onNoteChange,
            onDateChange = onDateChange,
            onTimeChange = onTimeChange,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.weight(1f),
        )
        // Bottom: Save CTA. SaveBar uses `fillMaxWidth()` internally,
        // so within this panel it spans only the panel's width
        // (45% in split, 100% in compact) — no longer a giant
        // full-screen bar.
        SaveBar(
            canSave = state.canSave,
            isSaving = state.isSaving,
            onSave = onSave,
        )
    }
}

@Composable
private fun PostCallContent(
    state: PostCallUiState,
    client: Client,
    interaction: Interaction,
    onSelectOutcome: (CallOutcome) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.isRecovering) {
            item("recovery_banner") { RecoveryBanner() }
        }
        // CallSummaryCard (avatar + name + phone + duration) removed:
        // those fields live in the right-pane `PreCallReadOnlyPanel`
        // plus the green hero strip already shows duration. Keeping
        // them here was a triple-render of the same information.
        //
        // SectionLabel "Call outcome" removed: with the compact chip
        // grid below, the chips themselves are the section — adding
        // a header label was just visual noise.
        state.reasonLabel?.let { reason ->
            item("outcome_reason") {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        // Compact outcome selector — FilterChip grid in 2 rows instead
        // of the previous full-width OutcomeRow stack (~64dp × 7 ≈ 450dp).
        // Now ~120dp total, freeing screen height for the note input
        // below the fold-line that was previously hidden behind scroll.
        item("outcomes") {
            CompactOutcomeSelector(
                state = state,
                onSelect = onSelectOutcome,
                onDateChange = onDateChange,
                onTimeChange = onTimeChange,
            )
        }
        item("note") {
            OutlinedTextField(
                value = state.noteText,
                onValueChange = onNoteChange,
                placeholder = { Text(stringResource(R.string.postcall_note_placeholder)) },
                label = { Text(stringResource(R.string.postcall_note_label)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.isSaving,
            )
        }

        state.errorMessage?.let { msg ->
            item("error") {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CallSummaryCard(client: Client, interaction: Interaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = client.name, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
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
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatDuration(interaction.durationSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Compact outcome selector — `FilterChip` grid wrapped in `FlowRow`.
 *
 * Replaces the previous full-width [OutcomeRow] stack (~64dp tall
 * per outcome × 7 outcomes = ~450dp of mandatory scroll). With this
 * layout, 7 chips fit in roughly 2 rows of ~50dp each → ~110dp
 * total, freeing screen height for the note input below to be
 * visible without scrolling on most devices.
 *
 * The chip itself communicates the outcome via:
 *   - Leading icon (the existing per-outcome glyph from
 *     [com.project.vortex.callsagent.ui.theme.icon])
 *   - Label text
 *   - Selected state via M3 default chip colouring
 *
 * `Respondió` vs `No respondió` headers were dropped — the icon
 * already says "smile" vs "phone-missed" etc., and the agent
 * scans the chip set, not section headers.
 *
 * When `INTERESTED` is selected, the follow-up form expands inline
 * below the chip grid — same UX as the old [OutcomeSelector] but
 * compact.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactOutcomeSelector(
    state: PostCallUiState,
    onSelect: (CallOutcome) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    val outcomesToShow = state.allowedOutcomes.takeIf { it.isNotEmpty() }
        // NO_SELECTED is a placeholder, never a pickable button — exclude it
        // from the catch-all fallback shown when no allow-list was provided.
        ?: CallOutcome.values().filterNot { it == CallOutcome.NO_SELECTED }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            outcomesToShow.forEach { outcome ->
                FilterChip(
                    selected = state.selectedOutcome == outcome,
                    onClick = { onSelect(outcome) },
                    label = { Text(outcome.label()) },
                    leadingIcon = {
                        Icon(
                            imageVector = outcome.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        // When selected, the chip adopts the outcome's
                        // semantic palette so the agent gets the same
                        // visual feedback (green for INTERESTED, red
                        // for DO_NOT_CALL, etc.) without us drawing a full
                        // colour-coded card.
                        selectedContainerColor = outcome.palette().container,
                        selectedLabelColor = outcome.palette().onContainer,
                        selectedLeadingIconColor = outcome.palette().onContainer,
                    ),
                )
            }
        }
        // Follow-up form appears inline ONLY when the agent picks
        // INTERESTED — preserves the original OutcomeSelector behaviour.
        // No interest-level selector: the backend derives the client
        // state from the outcome alone.
        if (state.selectedOutcome == CallOutcome.INTERESTED &&
            state.showFollowUpForm
        ) {
            FollowUpForm(
                state = state,
                onDateChange = onDateChange,
                onTimeChange = onTimeChange,
            )
        }
    }
}

@Composable
private fun OutcomeSelector(
    state: PostCallUiState,
    onSelect: (CallOutcome) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    val outcomesToShow = state.allowedOutcomes.takeIf { it.isNotEmpty() }
        // NO_SELECTED is a placeholder, never a pickable button — exclude it
        // from the catch-all fallback shown when no allow-list was provided.
        ?: CallOutcome.values().filterNot { it == CallOutcome.NO_SELECTED }
    // Stable order: backend enum declaration order is already the order
    // HOW_IT_WORKS §5 prescribes (answered outcomes first, then the
    // non-answered ones).
    val answered = outcomesToShow.filter { it.isAnswered }
    val notAnswered = outcomesToShow.filterNot { it.isAnswered }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (answered.isNotEmpty()) {
            OutcomeSectionHeader(text = stringResource(R.string.postcall_section_answered))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                answered.forEach { outcome ->
                    OutcomeRow(
                        outcome = outcome,
                        selected = state.selectedOutcome == outcome,
                        onSelect = { onSelect(outcome) },
                    )
                    // When INTERESTED is picked, expand the follow-up
                    // date/time form inline under the row so the agent
                    // doesn't scroll. No interest-level selector — the
                    // backend derives the client state from the outcome.
                    if (outcome == CallOutcome.INTERESTED && state.showFollowUpForm) {
                        FollowUpForm(
                            state = state,
                            onDateChange = onDateChange,
                            onTimeChange = onTimeChange,
                        )
                    }
                }
            }
        }
        if (notAnswered.isNotEmpty()) {
            OutcomeSectionHeader(text = stringResource(R.string.postcall_section_not_answered))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notAnswered.forEach { outcome ->
                    OutcomeRow(
                        outcome = outcome,
                        selected = state.selectedOutcome == outcome,
                        onSelect = { onSelect(outcome) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun OutcomeRow(
    outcome: CallOutcome,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = outcome.palette()
    val border = if (selected) palette.onContainer else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) palette.container else MaterialTheme.colorScheme.surface

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Outcome icon — same color treatment as the row's palette.
            // Recognizable at a glance without reading the label, which
            // matters for the 7-button grid (HOW_IT_WORKS §5).
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = outcome.icon(),
                    contentDescription = null,
                    tint = palette.onContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = outcome.label(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) palette.onContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Trailing checkmark only when selected — keeps the row
            // height stable whether or not the agent has tapped it.
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = palette.onContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun FollowUpForm(
    state: PostCallUiState,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.postcall_followup_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerButton(
                    icon = Icons.Filled.CalendarMonth,
                    label = stringResource(R.string.postcall_picker_date_label),
                    value = state.followUpDate?.format(dateFormatter())
                        ?: stringResource(R.string.postcall_picker_date_placeholder),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                )
                PickerButton(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.postcall_picker_time_label),
                    value = state.followUpTime?.format(timeFormatter())
                        ?: stringResource(R.string.postcall_picker_time_placeholder),
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            state.followUpDateTimeError?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showDatePicker) {
        DateDialog(
            initialDate = state.followUpDate ?: LocalDate.now().plusDays(1),
            onDismiss = { showDatePicker = false },
            onConfirm = {
                onDateChange(it)
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        TimeDialog(
            initialTime = state.followUpTime ?: LocalTime.of(10, 0),
            onDismiss = { showTimePicker = false },
            onConfirm = {
                onTimeChange(it)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun PickerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // Material3 DatePicker treats `selectedDateMillis` as UTC midnight
    // of the picked day. Mixing system-zone math here shifts the date
    // by ±1 day in any non-UTC timezone (Panama is UTC-5). Stay in
    // UTC for both the initial seed AND the confirm extraction so the
    // round-trip is lossless.
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    onConfirm(date)
                }
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.postcall_time_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onConfirm(LocalTime.of(state.hour, state.minute))
                    }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

@Composable
private fun SaveBar(canSave: Boolean, isSaving: Boolean, onSave: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.common_save),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.postcall_recovery_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.postcall_recovery_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return when {
        mins == 0 -> "${secs}s"
        secs == 0 -> "${mins}m"
        else -> "${mins}m ${secs}s"
    }
}

/**
 * Top-of-screen hero rendered in place of a plain [TopAppBar] —
 * mirrors the visual treatment of [InCallScreen]'s status hero so
 * the transition from in-call → post-call feels like the same
 * surface evolving rather than two distinct screens.
 *
 * Shows:
 *  - Back arrow (top-left)
 *  - Client avatar + name
 *  - "Llamada finalizada · M:SS" pill (mirrors the "Call ended"
 *    badge that InCallScreen showed seconds earlier)
 *
 * Same gradient (`Teal700 → Teal900`) as the in-call panel.
 */
@Composable
private fun CallEndedHero(
    client: com.project.vortex.callsagent.domain.model.Client?,
    durationSeconds: Int,
    onBack: () -> Unit,
) {
    val gradient = Brush.verticalGradient(colors = listOf(Teal700, Teal900))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (client != null) {
                    Avatar(name = client.name, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.postcall_hero_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.postcall_hero_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // "Call ended" pill — same shape and tone as the InCallScreen
            // status pill, so the surface evolves without a visual reset.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 56.dp),
            ) {
                Surface(
                    shape = PillShape,
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.White),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.postcall_call_ended_pill,
                                formatDuration(durationSeconds),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
