package com.project.vortex.callsagent.presentation.precall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.StatusPalette
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette

// ─── New timeline-first components ─────────────────────────────────────

/**
 * Compact identity strip — replaces the old gradient mega-hero. One
 * row with avatar + name + phone + status pill. The stats line below
 * summarises attempts/last-call/outcome in plain text so the agent
 * gets the at-a-glance summary without a 340dp gradient block stealing
 * the viewport.
 */
@Composable
internal fun CompactHeader(
    client: Client,
    onBack: (() -> Unit)?,
    onStatusClick: () -> Unit,
    onScheduleClick: (() -> Unit)? = null,
) {
    // Compact-width adaptation: on phones, the header would otherwise
    // overflow horizontally (long names wrapping into 3+ lines, big
    // typography) and slide under the status bar (the parent Scaffold
    // zeroes its `contentWindowInsets` so we own the inset here). We
    // apply `statusBarsPadding` AND scale-down typography only when
    // the available width is Compact (<600dp).
    val isCompact = WindowSize.isCompactWidth
    val nameStyle = if (isCompact) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.headlineMedium
    }
    val avatarSize = if (isCompact) 48.dp else 56.dp

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                // Reserve the status-bar height so the title doesn't
                // slide under the system status icons on edge-to-edge.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // Top row: back button + circular avatar (kept consistent
            // with the ClientCard in the list — circular, not the
            // squared style of the reference mockup).
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                }
                Avatar(name = client.name, size = avatarSize)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // Source data often comes in ALL-CAPS; normalise
                        // to Title Case so the name reads as a person,
                        // not a shouted label. Helper is local to this
                        // file — see [toTitleCase].
                        text = client.name.toTitleCase(),
                        style = nameStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = nameStyle.lineHeight,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusPillCompact(client = client, onClick = onStatusClick)
                    }
                }
                // Calendar action — only shown when scheduling makes
                // sense for the current client. Terminal statuses
                // (CONVERTED, REMOVED) hide the button — those clients
                // can't be scheduled (D1).
                if (onScheduleClick != null && client.status.canBeScheduled()) {
                    IconButton(onClick = onScheduleClick) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(R.string.precall_schedule_follow_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Data grid — full client info per the mockup. Fields that
            // exist in the model but have no value render with "—"
            // so the layout stays consistent and the agent learns the
            // shape of "what we know about this client" by glance.
            // REF (loan reference) is NOT a model field — it only
            // appears when the backend supplied it via `extraData`,
            // otherwise omitted entirely.
            //
            // SALARIO uses the success accent color (green) — the
            // mockup's convention for "money figure". All other values
            // stay in the default `onSurface` tone.
            // Field labels resolved at the call site (composable) so the
            // grid follows the active locale; keyed into `remember` so the
            // list rebuilds when either the client or the labels change.
            val labelCedula = stringResource(R.string.precall_field_cedula)
            val labelPhone = stringResource(R.string.precall_field_phone)
            val labelSalary = stringResource(R.string.precall_field_salary)
            val labelSocialSecurity = stringResource(R.string.precall_field_social_security)
            val labelReference = stringResource(R.string.precall_field_reference)
            val dataFields = remember(
                client,
                labelCedula,
                labelPhone,
                labelSalary,
                labelSocialSecurity,
                labelReference,
            ) {
                buildList {
                    add(DataFieldSpec(labelCedula,
                        client.cedula?.takeIf { it.isNotBlank() } ?: "—",
                        accent = false))
                    add(DataFieldSpec(labelPhone, client.phone, accent = false))
                    add(DataFieldSpec(labelSalary,
                        client.salary?.let { formatSalary(it) } ?: "—",
                        accent = client.salary != null))
                    add(DataFieldSpec(labelSocialSecurity,
                        client.ssNumber?.takeIf { it.isNotBlank() } ?: "—",
                        accent = false))
                    (client.extraData["loanReference"] as? String)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(DataFieldSpec(labelReference, it, accent = false)) }
                }
            }
            // Order per mockup: data grid lives BETWEEN the name/pill
            // row and the divider — divider separates the header
            // block from the HISTORIAL section, NOT the name from
            // the data.
            Spacer(Modifier.height(12.dp))
            ClientDataGrid(fields = dataFields)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Per-field row spec for the header data grid. `accent` flips the
 * value's text colour to the success palette (green) so the field
 * reads as the "money" emphasis line — only SALARIO uses it today,
 * and only when the value is real (not the "—" placeholder).
 */
private data class DataFieldSpec(
    val label: String,
    val value: String,
    val accent: Boolean,
)

/**
 * Normalises an ALL-CAPS / arbitrary-case name string into Title Case.
 * Treats whitespace as the word separator. Spanish connectors (de, la,
 * del, los, las, y, e) stay lowercase except when they're the first
 * word, so "JUAN DE LA CRUZ" → "Juan de la Cruz".
 */
private fun String.toTitleCase(): String {
    val lowercaseConnectors = setOf("de", "la", "los", "las", "del", "y", "e")
    return trim()
        .split(Regex("\\s+"))
        .mapIndexed { index, word ->
            val lower = word.lowercase()
            if (index > 0 && lower in lowercaseConnectors) lower
            else lower.replaceFirstChar { it.uppercase() }
        }
        .joinToString(" ")
}

/**
 * Two-column grid of `LABEL: value` pairs with monospace values —
 * meant to render terse persistent client data (cedula, phone,
 * salary). Labels use the standard tracking-letter look (uppercase,
 * small, primary tint); values use the platform monospace family so
 * digits align column-to-column. Adapts to 1 or 2 columns based on
 * available width — but in this app the header is always wider than
 * 480dp so the 2-col layout always wins.
 */
@Composable
private fun ClientDataGrid(fields: List<DataFieldSpec>) {
    // Column count adapts to available width:
    //  - Compact (<600dp, typical phones): 2 columns. Three fields
    //    in one row was overflowing — phone numbers and cédulas wrap
    //    awkwardly. Two columns gives each field ~140-160dp, enough
    //    for the longest values without forcing a wrap.
    //  - Medium / Expanded (tablets): 3 columns matches the mockup
    //    (CÉDULA · TEL · SALARIO in row 1, SEG. SOCIAL · REF in row 2).
    val columns = if (WindowSize.isCompactWidth) 2 else 3
    val rows = fields.chunked(columns)

    rows.forEachIndexed { rowIndex, rowFields ->
        if (rowIndex > 0) Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            rowFields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.width(20.dp))
                DataField(
                    label = field.label,
                    value = field.value,
                    accent = field.accent,
                    modifier = Modifier.weight(1f),
                )
            }
            // Pad trailing slots so column widths stay aligned across
            // rows (last row may have fewer fields than `columns`).
            repeat(columns - rowFields.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DataField(
    label: String,
    value: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.precall_field_label_format, label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            // Accent rows render in success-green to catch the eye —
            // reserved for "money figure" semantics (SALARIO). All
            // other values stay in `onSurface` for neutral reading.
            color = if (accent) com.project.vortex.callsagent.ui.theme.Emerald600
                    else MaterialTheme.colorScheme.onSurface,
            // Single line + ellipsis: if a value is unusually long
            // (e.g. unformatted phone with country code), we'd rather
            // truncate than wrap and break the grid alignment.
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * "Estado" entry-point pill. Always reads the literal word "Estado"
 * plus a `▾` chevron — standard dropdown affordance: shows the CURRENT
 * selection ("Pending", "Interested", "Converted"…) plus the chevron
 * to signal "tap to change". `ClientStatus` is never null (PENDING is
 * the default on creation), so the pill always renders a real value
 * — no "Estado" placeholder needed. The status row was therefore
 * removed from the data grid below to eliminate the duplication.
 */
@Composable
private fun StatusPillCompact(client: Client, onClick: () -> Unit) {
    // Override the global status palette LOCALLY to match the mockup,
    // without affecting how status pills look on the list / agenda
    // screens. PENDING here renders in amber (the mockup convention
    // "needs attention"); other states fall back to the global theme
    // palette. Label comes from the shared [ClientStatus.label] helper
    // so the 5-state vocabulary stays consistent app-wide.
    val palette = precallStatusPalette(client.status)
    val label = client.status.label()
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = palette.container,
        contentColor = palette.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.onContainer),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "▾",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Local PENDING-amber palette override for PreCall — matches the
 * mockup's "needs attention" pill colour without touching the global
 * status palette (which other screens rely on for neutral pills).
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun precallStatusPalette(status: ClientStatus): StatusPalette = when (status) {
    ClientStatus.PENDING -> precallAmberPalette()
    else -> status.palette()
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun precallAmberPalette(): StatusPalette =
    if (MaterialTheme.colorScheme.surface.precallLuminance() < 0.5f) {
        StatusPalette(
            container = com.project.vortex.callsagent.ui.theme.Amber600.copy(alpha = 0.25f),
            onContainer = com.project.vortex.callsagent.ui.theme.Amber100,
        )
    } else {
        StatusPalette(
            container = com.project.vortex.callsagent.ui.theme.Amber100,
            onContainer = com.project.vortex.callsagent.ui.theme.Amber600,
        )
    }

private fun Color.precallLuminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

internal fun formatSalary(amount: Double): String {
    // Whole-number USD when possible (most seeded values are integers)
    // — falls back to two decimals otherwise.
    val whole = amount.toLong()
    return if (amount == whole.toDouble()) "$%,d".format(whole)
    else "$%,.2f".format(amount)
}

/**
 * Whether the agent can schedule a follow-up for a client in this
 * status. Scheduling only makes sense while the client is still active
 * in the funnel — the terminal states (CONVERTED, REMOVED) are
 * excluded; the agent has to reactivate first (admin path) before
 * scheduling.
 *
 * D1 from the design discussion: only the active states
 * (PENDING / INTERESTED / CITED) are schedulable from the mobile
 * agent's perspective.
 */
private fun ClientStatus.canBeScheduled(): Boolean = when (this) {
    ClientStatus.PENDING,
    ClientStatus.INTERESTED,
    ClientStatus.CITED -> true
    ClientStatus.CONVERTED,
    ClientStatus.REMOVED -> false
}
