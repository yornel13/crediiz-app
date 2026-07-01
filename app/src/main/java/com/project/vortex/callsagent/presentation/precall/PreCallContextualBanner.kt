package com.project.vortex.callsagent.presentation.precall

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.ui.theme.label
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ─── Contextual banner ────────────────────────────────────────────────

/**
 * Computed banner state. Picks the FIRST applicable context so we
 * never stack banners. Priority order is intentional — terminal
 * statuses dominate (a REMOVED client's scheduled callback is
 * meaningless).
 */
internal sealed interface BannerContext {
    data object Converted : BannerContext
    data class Removed(val reason: RemovalReason?) : BannerContext
    data class ScheduledCallback(val at: Instant) : BannerContext
    data object NewLead : BannerContext
}

internal fun computeBannerContext(
    client: Client,
    nextFollowUp: FollowUp?,
    activityCount: Int,
): BannerContext? = when {
    client.status == ClientStatus.CONVERTED -> BannerContext.Converted
    client.status == ClientStatus.REMOVED -> BannerContext.Removed(client.removalReason)
    nextFollowUp != null -> BannerContext.ScheduledCallback(nextFollowUp.scheduledAt)
    activityCount == 0 && client.callAttempts == 0 -> BannerContext.NewLead
    else -> null
}

@Composable
internal fun ContextualBanner(context: BannerContext, modifier: Modifier = Modifier) {
    val (icon, container, onContainer, title, subtitle) = when (context) {
        BannerContext.Converted -> BannerStyle(
            icon = Icons.Filled.CheckCircle,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            title = stringResource(R.string.precall_banner_converted_title),
            subtitle = stringResource(R.string.precall_banner_converted_subtitle),
        )
        is BannerContext.Removed -> BannerStyle(
            icon = Icons.Filled.Block,
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
            title = stringResource(R.string.precall_banner_do_not_call_title),
            // Subtitle surfaces the concrete removal reason when the
            // backend supplied one (REMOVED always carries a
            // RemovalReason in the new model); falls back to the
            // generic opt-out copy if it's ever null.
            subtitle = context.reason?.label()
                ?: stringResource(R.string.precall_banner_do_not_call_subtitle),
        )
        is BannerContext.ScheduledCallback -> BannerStyle(
            icon = Icons.Filled.Schedule,
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            title = stringResource(
                R.string.precall_banner_scheduled_callback_title,
                formatScheduledRelative(context.at),
            ),
            subtitle = formatTimestamp(context.at),
        )
        BannerContext.NewLead -> BannerStyle(
            icon = Icons.Filled.Star,
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(R.string.precall_banner_new_lead_title),
            subtitle = stringResource(R.string.precall_banner_new_lead_subtitle),
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = onContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private data class BannerStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val container: Color,
    val onContainer: Color,
    val title: String,
    val subtitle: String,
)

@Composable
private fun formatScheduledRelative(at: Instant): String {
    val now = Instant.now()
    // Business clock — see BusinessConfig. The "today / tomorrow /
    // overdue" labels must match the admin/client calendar; otherwise
    // an agent in Caracas sees a follow-up land in a different bucket
    // than the admin who scheduled it.
    val zone = BusinessConfig.BUSINESS_TIMEZONE
    val today = LocalDate.now(zone)
    val target = at.atZone(zone).toLocalDate()
    return when {
        target == today && at.isAfter(now) ->
            stringResource(R.string.precall_relative_today)
        target == today && at.isBefore(now) ->
            stringResource(R.string.precall_relative_overdue)
        target == today.plusDays(1) ->
            stringResource(R.string.precall_relative_tomorrow)
        target.isAfter(today) -> {
            val days = ChronoUnit.DAYS.between(today, target).toInt()
            pluralStringResource(R.plurals.precall_relative_in_days, days, days)
        }
        else -> stringResource(R.string.precall_relative_overdue)
    }
}
