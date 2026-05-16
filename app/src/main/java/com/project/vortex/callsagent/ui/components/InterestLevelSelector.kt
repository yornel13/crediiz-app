package com.project.vortex.callsagent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.ui.theme.emoji
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette

/**
 * Editable thermometer — three large chips in a row, one per
 * [InterestLevel]. Tapping a chip emits [onSelect] immediately; the
 * caller is responsible for the optimistic + PATCH choreography.
 *
 * Used in:
 *  - PostCall when the agent picks `ANSWERED_INTERESTED` (initial set).
 *  - PreCall bottom sheet when the agent re-classifies an existing
 *    INTERESTED client between calls.
 *
 * `null` for [selected] is allowed so the form can start "unset" —
 * e.g. when entering PostCall with INTERESTED for the first time and
 * the agent hasn't tapped yet.
 */
@Composable
fun InterestLevelSelector(
    selected: InterestLevel?,
    onSelect: (InterestLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Nivel de interés",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InterestLevel.values().forEach { level ->
                LevelChip(
                    level = level,
                    selected = selected == level,
                    onClick = { onSelect(level) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LevelChip(
    level: InterestLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = level.palette()
    val borderColor = if (selected) palette.onContainer else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) palette.container else MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .background(container)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = level.emoji(),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = level.label(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) palette.onContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
