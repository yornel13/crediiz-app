package com.project.vortex.callsagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.ui.theme.emoji
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette

/**
 * Compact read-only badge showing the thermometer level of an
 * INTERESTED client. Used in list/agenda cards.
 *
 * Visual: pill with the color emoji + the Spanish label
 * ("🟦 Frío", "🟧 Tibio", "🟥 Caliente"). Container color follows
 * the level's brand palette so it reads as part of the card.
 *
 * Callers should hide this composable entirely when the client is
 * not INTERESTED — there's no "no thermometer" state to render.
 */
@Composable
fun InterestLevelChip(
    level: InterestLevel,
    modifier: Modifier = Modifier,
) {
    val palette = level.palette()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "${level.emoji()} ${level.label()}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.onContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
