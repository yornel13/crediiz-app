package com.project.vortex.callsagent.presentation.clients.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.presentation.clients.ClientsViewKind

/**
 * Segmented pill selector for the three Clients sub-views. Modeled after
 * the design in `docs/CLIENTS_TAB_REDESIGN.md § 4.1` — explicitly NOT a
 * `TabRow` because the bottom nav already uses tabs and a second nested
 * tab strip looks amateurish on the Tab A9.
 */
@Composable
fun ClientsViewSelector(
    selected: ClientsViewKind,
    pendingCount: Int,
    recentCount: Int,
    interestedCount: Int,
    onSelected: (ClientsViewKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClientsViewKind.entries.forEach { kind ->
            val count = when (kind) {
                ClientsViewKind.PENDIENTES -> pendingCount
                ClientsViewKind.RECIENTES -> recentCount
                ClientsViewKind.INTERESADOS -> interestedCount
            }
            ViewPill(
                label = kind.labelEs,
                count = count,
                isSelected = selected == kind,
                onClick = { onSelected(kind) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ViewPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    val labelColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = container,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = labelColor,
            )
            Spacer(Modifier.height(2.dp))
            // Count slides vertically when it changes — same micro-interaction
            // pattern used by the sync indicator.
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(180)) { it / 2 } +
                        fadeIn(animationSpec = tween(180))) togetherWith
                        (slideOutVertically(animationSpec = tween(180)) { -it / 2 } +
                            fadeOut(animationSpec = tween(180)))
                },
                label = "view-pill-count",
                modifier = Modifier.wrapContentSize(),
            ) { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                )
            }
        }
    }
}
