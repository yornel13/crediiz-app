package com.project.vortex.callsagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project.vortex.callsagent.ui.theme.Amber600
import com.project.vortex.callsagent.ui.theme.Coral500
import com.project.vortex.callsagent.ui.theme.Emerald600
import com.project.vortex.callsagent.ui.theme.Rose600
import com.project.vortex.callsagent.ui.theme.Sky600
import com.project.vortex.callsagent.ui.theme.Slate700
import com.project.vortex.callsagent.ui.theme.Teal700
import kotlin.math.absoluteValue

private val AvatarPalette = listOf(
    Teal700,
    Emerald600,
    Sky600,
    Amber600,
    Coral500,
    Rose600,
    Slate700,
)

/**
 * A circular avatar showing up to two initials of [name] over a deterministic
 * color picked from a fixed palette. Same name → same color across the app.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val initials = remember(name) { initialsOf(name) }
    val color = remember(name) {
        AvatarPalette[(name.hashCode().absoluteValue) % AvatarPalette.size]
    }
    val fontSize = (size.value * 0.38f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}
