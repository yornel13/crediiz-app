package com.project.vortex.callsagent.presentation.precall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.domain.model.ActivityEvent
import com.project.vortex.callsagent.ui.theme.label
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Renders the activity timeline as a vertical visual rail: a 2-dp
 * connector line runs floor-to-ceiling on the left, with a colored
 * dot at each event's y-position and the card content to the right.
 *
 * Color semantics on the dots:
 *  - Top item (`index == 0` and event is *not* a [LeadImported]):
 *    green, hollow ring → "tu próxima acción / posición actual".
 *  - Older recent items (within the same first bucket / today):
 *    primary tint solid → currently relevant.
 *  - All other items: muted surface variant solid → historical.
 *
 * The bucket headers ("HOY", "AYER", "12 OCT") sit BETWEEN the rail
 * and the card column so they don't break the vertical line; instead
 * we draw a short tick mark across the rail at the bucket boundary.
 */
internal fun LazyListScope.renderActivityTimeline(events: List<ActivityEvent>) {
    // Per-day bucket headers ("Today", "Yesterday"…) were removed —
    // each card already carries its own absolute/relative timestamp in
    // the top-right corner, so the extra label was visual noise. The
    // continuous left rail + connector dots provides the temporal
    // grouping affordance.
    events.forEachIndexed { index, event ->
        item(event.stableKey) {
            ActivityRail(
                event = event,
                isFirst = index == 0,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * One row of the activity rail: dot/line gutter on the left + card
 * on the right. The vertical connector line is drawn as a 2-dp Box
 * in the gutter column whose height fills the row — that way
 * adjacent rows visually stack into a continuous line without any
 * Canvas coordinate juggling.
 */
@Composable
private fun ActivityRail(
    event: ActivityEvent,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ActivityRailGutter(
            isFirst = isFirst,
            event = event,
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier
            .weight(1f)
            // 6dp top + 6dp bottom = 12dp gap between adjacent cards
            // without breaking the continuous rail line behind them.
            .padding(vertical = 6.dp)) {
            ActivityRow(event = event)
        }
    }
}

/**
 * The left-hand gutter that carries the vertical rail + dot. Computes
 * dot color/style from event kind and position (first / lead-import /
 * everything else).
 */
@Composable
private fun ActivityRailGutter(
    isFirst: Boolean,
    event: ActivityEvent,
) {
    val (dotColor, dotIsHollow) = when {
        // Top-most non-anchor event = "you are here". Hollow GREEN
        // ring — the only coloured dot in the rail (mockup convention:
        // "verde = lo importante / lo nuevo"). Anchors (LeadImported,
        // AssignedToAgent) never get the ring even when they're first,
        // because they're context markers, not fresh actions.
        isFirst &&
            event !is ActivityEvent.LeadImported &&
            event !is ActivityEvent.AssignedToAgent ->
            com.project.vortex.callsagent.ui.theme.Emerald600 to true
        // Anchor markers — muted.
        event is ActivityEvent.LeadImported ||
            event is ActivityEvent.AssignedToAgent ->
            MaterialTheme.colorScheme.outline to false
        // All past events render as neutral grey dots — the mockup
        // shows uniform muted dots below the green ring, no
        // differentiation by event type. Type info already lives
        // inside the card (icon + label), so the rail dot doesn't
        // need to carry it again.
        else -> MaterialTheme.colorScheme.onSurfaceVariant to false
    }
    // Gutter = thin vertical connector line + dot. The line runs the
    // full row height behind the dot; adjacent rows stack their lines
    // into a continuous rail. `outlineVariant` keeps it subtle (the
    // "clarita" the mockup shows) — visible enough to read as a
    // timeline, quiet enough not to compete with the cards.
    val railColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Connector line — 1dp wide, full-row-height, centered.
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(railColor),
        )
        // Dot container — always 10dp so the hollow ring fits, plus
        // gives the smaller solid dot a constant centering anchor.
        // Avatar center inside the card sits at 6dp (wrapper) +
        // 14dp (card padding) + 12dp (half of 24dp avatar) = 32dp
        // from row top. Container top = 32 - 5 = 27dp.
        Box(
            modifier = Modifier
                .padding(top = 27.dp)
                .size(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (dotIsHollow) {
                // Green "you are here" hollow ring — 10dp outer.
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            } else {
                // Past-event dot — 6dp solid (smaller than the green
                // ring) so the "current position" marker stays the
                // heaviest visual element in the rail.
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}

// ─── Activity rows ────────────────────────────────────────────────────

@Composable
private fun ActivityRow(event: ActivityEvent, modifier: Modifier = Modifier) {
    when (event) {
        is ActivityEvent.Call -> CallActivityRow(event, modifier)
        is ActivityEvent.NoteEntry -> NoteActivityRow(event, modifier)
        is ActivityEvent.FollowUpScheduled -> FollowUpActivityRow(event, modifier)
        is ActivityEvent.StatusChanged -> StatusChangeActivityRow(event, modifier)
        is ActivityEvent.LeadImported -> LeadImportedRow(modifier)
        is ActivityEvent.AssignedToAgent -> AssignedToAgentRow(event, modifier)
    }
}

/**
 * Status transition from the canonical history (any actor). Reuses the
 * shared [ActivityRowPlain] shell: title "Cambio de estado", the author /
 * source as meta, and "from → to" (+ optional reason) as the body — so the
 * agent can see who moved the client and why, mixed into the timeline.
 */
@Composable
private fun StatusChangeActivityRow(event: ActivityEvent.StatusChanged, modifier: Modifier) {
    // When the client was removed, append the reason ("Desistido · No
    // localizable") so the agent sees *why* — not just *that* it dropped.
    val toLabel = event.removalReason
        ?.let { "${event.toStatus.label()} · ${it.label()}" }
        ?: event.toStatus.label()
    val transition = if (event.fromStatus != null) {
        "${event.fromStatus.label()} → $toLabel"
    } else {
        toLabel
    }
    val body = event.reason?.let { "$transition\n\"$it\"" } ?: transition
    val author = event.changedByName
    val role = event.changedByRole?.let { statusChangeRoleLabel(it) }
    val meta = when {
        author != null && role != null -> "$author · $role"
        author != null -> author
        else -> event.source?.let { statusChangeSourceLabel(it) }
    }
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_status_change),
        meta = meta,
        timestamp = formatActivityTimestamp(event.occurredAt),
        body = body,
        icon = Icons.Filled.SwapHoriz,
        modifier = modifier,
    )
}

@Composable
private fun statusChangeRoleLabel(role: com.project.vortex.callsagent.common.enums.Role): String =
    stringResource(
        when (role) {
            com.project.vortex.callsagent.common.enums.Role.ADMIN -> R.string.precall_role_admin
            com.project.vortex.callsagent.common.enums.Role.AGENT -> R.string.precall_role_agent
        },
    )

@Composable
private fun statusChangeSourceLabel(
    source: com.project.vortex.callsagent.common.enums.StatusChangeSource,
): String = stringResource(
    when (source) {
        com.project.vortex.callsagent.common.enums.StatusChangeSource.INITIAL_LOAD ->
            R.string.precall_source_initial_load
        com.project.vortex.callsagent.common.enums.StatusChangeSource.CALL_OUTCOME ->
            R.string.precall_source_call_outcome
        com.project.vortex.callsagent.common.enums.StatusChangeSource.AGENT_OUT_OF_BAND ->
            R.string.precall_source_agent_out_of_band
        com.project.vortex.callsagent.common.enums.StatusChangeSource.ADMIN_MANUAL ->
            R.string.precall_source_admin_manual
        com.project.vortex.callsagent.common.enums.StatusChangeSource.ADMIN_REACTIVATE ->
            R.string.precall_source_admin_reactivate
        com.project.vortex.callsagent.common.enums.StatusChangeSource.AGENT_DISMISSAL ->
            R.string.precall_source_agent_dismissal
        com.project.vortex.callsagent.common.enums.StatusChangeSource.AGENT_DISMISSAL_UNDONE ->
            R.string.precall_source_agent_dismissal_undone
    },
)

/**
 * Single call event rendered as plain text on the screen background
 * (no Card wrapper) — matches the reference mockup where the timeline
 * reads as a continuous log, not a stack of cards.
 *
 * Layout:
 *   [Phone-icon avatar][Llamada · 2:34]                  [HOY/AYER, h:mm a]
 *                       [Human description of outcome]
 *
 * The title is the literal "Llamada" — we explicitly DON'T show
 * "Sistema" anymore because (a) agentId isn't tracked locally, so the
 * old label was a placeholder, and (b) the icon already communicates
 * the type, making a generic author redundant.
 */
@Composable
private fun CallActivityRow(event: ActivityEvent.Call, modifier: Modifier) {
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_call),
        meta = formatDuration(event.durationSeconds),
        timestamp = formatActivityTimestamp(event.occurredAt),
        // Use the centralized [CallOutcome.label] helper so the call
        // log speaks the same 13-value vocabulary as the rest of the
        // app (PostCall grid, agenda) instead of a local narrative map.
        body = event.outcome.label(),
        icon = Icons.Filled.Phone,
        modifier = modifier,
    )
}

/**
 * Single note event — same shape as [CallActivityRow] but with the
 * "Nota" title and the notes icon. Avatar styling is intentionally
 * uniform across event types (see [ActivityRowPlain]).
 */
@Composable
private fun NoteActivityRow(event: ActivityEvent.NoteEntry, modifier: Modifier) {
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_note),
        meta = null,
        timestamp = formatActivityTimestamp(event.occurredAt),
        body = event.content,
        icon = Icons.AutoMirrored.Filled.Notes,
        modifier = modifier,
    )
}

/**
 * Shared card layout for call and note events. Lives inside the
 * activity rail and renders inside a rounded Surface — matches the
 * reference mockup where each event reads as its own grouped block,
 * not free-floating text.
 *
 * Layout inside the card:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ [Icon avatar]  Title · meta              HOY, 14:20      │
 *   │                                                          │
 *   │ Body text spanning the full card width, multi-line ok.   │
 *   └──────────────────────────────────────────────────────────┘
 *
 * `title` is the type label ("Nota", "Llamada"); `meta` is an optional
 * secondary fragment after a `·` separator (e.g. call duration). The
 * avatar shows an icon matching the event type, set by the caller.
 */
@Composable
private fun ActivityRowPlain(
    title: String,
    meta: String?,
    timestamp: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // `surfaceContainerLow` (one step above `background`) gives the
        // subtle card-on-canvas separation the mockup shows in dark
        // theme without competing with content cards above.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        // 1-dp outline using `outlineVariant` — matches the mockup's
        // subtle card edge. Without the border the cards bled into
        // the background in dark theme at low container contrast.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header row: avatar + title/meta on the left, timestamp
            // pinned to the right. Avatar styling is uniform across
            // event types — a neutral dark circle with the type icon
            // in `onSurfaceVariant`. Differentiation comes from the
            // ICON inside the circle (phone, note…), not the color of
            // the circle, mirroring the mockup.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventTypeAvatar(
                    icon = icon,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = title,
                )
                Spacer(Modifier.width(12.dp))
                // Title + meta share a single weighted slot so they YIELD
                // space to the timestamp on narrow phones. The meta (e.g.
                // "Admin · Admin") truncates with an ellipsis instead of
                // pushing the timestamp into a 1-char-per-line wrap. The
                // timestamp itself has no weight (intrinsic width) and
                // `softWrap = false`, so it is measured first and always
                // renders on one line.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (meta != null) {
                        Text(
                            text = "  ·  $meta",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                // Muted body color (onSurfaceVariant) is intentional:
                // it sets the visual hierarchy header→body, matching
                // the reference mockup. Using `onSurface` (~white in
                // dark theme) flattens the hierarchy and the title
                // stops "leading" the eye.
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }
    }
}

/**
 * Small (~28dp) circular avatar with an icon representing the activity
 * TYPE (note, call, follow-up…). Previously rendered author initials,
 * but since the schema doesn't carry agentId per event yet, an icon is
 * both more honest ("this is a call") and visually richer than the
 * deterministic-hash-color initials of a "Sistema" / "Nota" string.
 *
 * Color comes from the caller so each event type can pick a tone that
 * matches its semantic family (primary/note, secondary/call, etc.).
 */
@Composable
private fun EventTypeAvatar(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun FollowUpActivityRow(event: ActivityEvent.FollowUpScheduled, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.precall_followup_scheduled),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = formatTimestamp(event.scheduledFor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun LeadImportedRow(modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Emoji stays inline (non-translatable symbol); only the
            // label text is localised.
            text = "✨  " + stringResource(R.string.precall_lead_imported),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Assignment event rendered as a regular timeline card — same shape
 * as [CallActivityRow] and [NoteActivityRow] (avatar circle with
 * type icon, title, timestamp top-right, body line). Tells the
 * agent "this is when the admin handed you this client" with the
 * same visual weight as the rest of the timeline so it doesn't
 * feel like a stray annotation.
 */
@Composable
private fun AssignedToAgentRow(
    event: ActivityEvent.AssignedToAgent,
    modifier: Modifier,
) {
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_assigned_title),
        meta = null,
        timestamp = formatActivityTimestamp(event.occurredAt),
        body = stringResource(R.string.precall_activity_assigned_body),
        icon = Icons.Filled.PersonAdd,
        modifier = modifier,
    )
}


@Composable
internal fun ActivityEmptyState(showAll: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "✨", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (showAll) {
                stringResource(R.string.precall_empty_all_title)
            } else {
                stringResource(R.string.precall_empty_notes_title)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (showAll) {
                stringResource(R.string.precall_empty_all_body)
            } else {
                stringResource(R.string.precall_empty_notes_body)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

// 12-hour clock with AM/PM suffix — matches the rest of the app
// (AgendaScreen, PostCallScreen, ScheduleFollowUpSheet all use
// `h:mm a`). Built per-call with the process locale so AM/PM and
// month names follow the language chosen at runtime (Activities are
// recreated on language change — see ui/locale/LocaleContext).
private fun timeOnlyFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

private fun dateMonthFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())

/**
 * Absolute timestamp suited to a chronological activity log:
 *  - Same day: "HOY, 2:20 p. m."
 *  - Yesterday: "AYER, 9:15 a. m."
 *  - This week: "MAR, 2:20 p. m." (weekday abbrev + time)
 *  - Older: "12 OCT, 11:02 a. m."
 *
 * The agent gets factual time without having to mentally subtract
 * "hace X horas". Useful for audit trails too.
 */
@Composable
private fun formatActivityTimestamp(instant: Instant): String {
    // Business clock — "HOY"/"AYER" labels match the admin calendar.
    // See BusinessConfig.
    val zone = BusinessConfig.BUSINESS_TIMEZONE
    val now = LocalDate.now(zone)
    val zoned = instant.atZone(zone)
    val date = zoned.toLocalDate()
    val time = timeOnlyFormatter().format(zoned)
    return when {
        date == now -> stringResource(R.string.precall_ts_today_format, time)
        date == now.minusDays(1) ->
            stringResource(R.string.precall_ts_yesterday_format, time)
        ChronoUnit.DAYS.between(date, now) in 2..6 -> {
            val day = date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                .uppercase().take(3)
            "$day, $time"
        }
        else -> "${dateMonthFormatter().format(zoned).uppercase()}, $time"
    }
}

private fun timestampFormatter(): DateTimeFormatter =
    // 12-hour clock — see timeOnlyFormatter rationale above.
    DateTimeFormatter.ofPattern("d MMM, h:mm a", java.util.Locale.getDefault())

internal fun formatTimestamp(instant: Instant): String =
    // Business clock everywhere — see BusinessConfig.
    timestampFormatter().format(instant.atZone(BusinessConfig.BUSINESS_TIMEZONE))
