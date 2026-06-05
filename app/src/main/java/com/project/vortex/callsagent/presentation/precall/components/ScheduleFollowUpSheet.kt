package com.project.vortex.callsagent.presentation.precall.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.project.vortex.callsagent.ui.components.FullHeightBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.ui.components.InterestLevelSelector
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Payload emitted when the agent confirms the schedule sheet.
 *
 * - [interestLevel] is **always** set even if the client wasn't
 *   INTERESTED before — see HOW_IT_WORKS §4: scheduling implies
 *   INTERESTED. The ViewModel uses it to drive the
 *   agent-status-change transition if needed.
 * - [replacePending] tells the caller "yes, I already saw the
 *   conflict warning and want to override the existing follow-up".
 */
data class ScheduleFollowUpResult(
    val scheduledAt: Instant,
    val reason: String?,
    val interestLevel: InterestLevel,
    val replacePending: Boolean,
)

/**
 * Sheet that lets the agent create a follow-up **without making a
 * call** (HOW_IT_WORKS §7 extension — agent-driven scheduling for
 * out-of-band signals like WhatsApp).
 *
 * Behavior contract:
 *  - Picks date + time, both required.
 *  - Shows [InterestLevelSelector] always (default COLD). Picking a
 *    higher level promotes the client when ViewModel commits.
 *  - [existingFollowUp] (when non-null) renders a warning + forces
 *    the agent to acknowledge replacement before Confirm enables.
 *  - Reason is optional; the agent already has the date/time in the
 *    card, free-form text is just context for later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFollowUpSheet(
    clientName: String,
    existingFollowUp: FollowUp?,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleFollowUpResult) -> Unit,
) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    var selectedLevel by remember { mutableStateOf(InterestLevel.COLD) }
    var reason by remember { mutableStateOf("") }
    var replaceAcknowledged by remember { mutableStateOf(existingFollowUp == null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val isFuture = remember(selectedDate, selectedTime) {
        val d = selectedDate
        val t = selectedTime
        if (d == null || t == null) false
        // Interpret the picker against the BUSINESS clock — see
        // BusinessConfig kdoc. systemDefault() would silently shift
        // the threshold by the agent's local-vs-Panama offset.
        else d.atTime(t).atZone(BusinessConfig.BUSINESS_TIMEZONE)
            .toInstant().isAfter(Instant.now())
    }
    val canConfirm = selectedDate != null &&
        selectedTime != null &&
        isFuture &&
        replaceAcknowledged

    FullHeightBottomSheet(onDismissRequest = onDismiss) {
        // Outer column owns the full sheet height. Body scrolls in
        // the middle, footer (Cancel + Confirm) stays pinned at the
        // bottom so the agent never loses the CTAs even with long
        // forms or with the IME open.
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ─── Header ─────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.schedule_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = clientName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ─── Conflict warning ──────────────────────────────
                if (existingFollowUp != null) {
                    ExistingFollowUpWarning(
                        existing = existingFollowUp,
                        acknowledged = replaceAcknowledged,
                        onAcknowledge = { replaceAcknowledged = true },
                        onCancel = onDismiss,
                    )
                }

                // ─── Date / Time pickers ────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PickerButton(
                        icon = Icons.Filled.CalendarMonth,
                        label = stringResource(R.string.schedule_date_label),
                        value = selectedDate?.format(dateFormatter())
                            ?: stringResource(R.string.schedule_picker_choose),
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                    PickerButton(
                        icon = Icons.Filled.Schedule,
                        label = stringResource(R.string.schedule_time_label),
                        value = selectedTime?.format(timeFormatter())
                            ?: stringResource(R.string.schedule_picker_choose),
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selectedDate != null && selectedTime != null && !isFuture) {
                    Text(
                        text = stringResource(R.string.schedule_future_date_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // ─── Interest level (always visible) ────────────────
                InterestLevelSelector(
                    selected = selectedLevel,
                    onSelect = { selectedLevel = it },
                )

                // ─── Reason (optional) — textarea ───────────────────
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= MAX_REASON_CHARS) reason = it },
                    label = { Text(stringResource(R.string.schedule_reason_label)) },
                    placeholder = { Text(stringResource(R.string.schedule_reason_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.schedule_char_counter,
                                reason.length,
                                MAX_REASON_CHARS,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )

                // Bottom breathing room so the textfield's helper line
                // doesn't kiss the footer divider.
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }

            // ─── Sticky footer ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = {
                        val d = selectedDate ?: return@Button
                        val t = selectedTime ?: return@Button
                        // CRITICAL: business clock, not device clock. A Venezuelan
                        // agent picking "tomorrow 2pm" means 2pm Panama (when the
                        // client is available), not 2pm Caracas. See BusinessConfig.
                        val instant = d.atTime(t).atZone(BusinessConfig.BUSINESS_TIMEZONE).toInstant()
                        onConfirm(
                            ScheduleFollowUpResult(
                                scheduledAt = instant,
                                reason = reason.trim().ifBlank { null },
                                interestLevel = selectedLevel,
                                replacePending = existingFollowUp != null,
                            ),
                        )
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (existingFollowUp != null) {
                            stringResource(R.string.schedule_action_replace)
                        } else {
                            stringResource(R.string.schedule_action_schedule)
                        },
                    )
                }
            }
        }
    }

    // ─── DatePicker dialog ──────────────────────────────────────────
    if (showDatePicker) {
        val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val initial = selectedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            ?: today
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // DatePicker returns UTC midnight — read it back in UTC
                        // and let the local-time picker fill in the hour. Using
                        // systemDefault() here would shift the date by one day
                        // for negative-offset timezones.
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    // ─── TimePicker dialog ──────────────────────────────────────────
    if (showTimePicker) {
        val now = LocalTime.now()
        val state = rememberTimePickerState(
            initialHour = selectedTime?.hour ?: now.hour,
            initialMinute = selectedTime?.minute ?: now.minute,
            is24Hour = false,
        )
        ModalBottomSheet(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TimePicker(state = state)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showTimePicker = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(onClick = {
                        selectedTime = LocalTime.of(state.hour, state.minute)
                        showTimePicker = false
                    }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

@Composable
private fun ExistingFollowUpWarning(
    existing: FollowUp,
    acknowledged: Boolean,
    onAcknowledge: () -> Unit,
    onCancel: () -> Unit,
) {
    val existingLabel = remember(existing.scheduledAt) {
        // Render in business clock so the warning matches what the agent
        // will see in the agenda — see BusinessConfig.
        val dt = existing.scheduledAt.atZone(BusinessConfig.BUSINESS_TIMEZONE)
        DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", java.util.Locale.getDefault())
            .format(dt)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.schedule_existing_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = existingLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
            if (!existing.reason.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.schedule_existing_reason_quote, existing.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }
            Text(
                text = stringResource(R.string.schedule_existing_replace_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!acknowledged) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.schedule_action_replace)) }
                }
            }
        }
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Built per-call with the process locale so weekday/month names and
// AM/PM follow the language chosen at runtime (Activities are recreated
// on language change — see ui/locale/LocaleContext).
private fun dateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.getDefault())

private fun timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

private const val MAX_REASON_CHARS = 200
