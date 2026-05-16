package com.project.vortex.callsagent.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.common.enums.NoteType

/**
 * A semantic color pair (background + foreground) used for badges, chips,
 * and status indicators. Both colors are picked to remain readable on the
 * current Material color scheme (light or dark).
 */
data class StatusPalette(
    val container: Color,
    val onContainer: Color,
)

@Composable
@ReadOnlyComposable
fun ClientStatus.palette(): StatusPalette = when (this) {
    ClientStatus.PENDING -> neutralPalette()
    ClientStatus.IN_PROGRESS -> warningPalette()
    ClientStatus.INTERESTED -> successPalette()
    ClientStatus.REJECTED -> errorPalette()
    ClientStatus.UNREACHABLE -> errorPalette()
    ClientStatus.CONVERTED -> infoPalette()
    ClientStatus.DO_NOT_CALL -> errorPalette()
    ClientStatus.DISMISSED -> neutralPalette()
}

@Composable
@ReadOnlyComposable
fun CallOutcome.palette(): StatusPalette = when (this) {
    CallOutcome.ANSWERED_INTERESTED -> successPalette()
    CallOutcome.ANSWERED_NOT_INTERESTED -> errorPalette()
    CallOutcome.ANSWERED_OPT_OUT -> errorPalette()
    CallOutcome.ANSWERED_SOLD -> infoPalette()
    CallOutcome.NO_ANSWER -> warningPalette()
    CallOutcome.BUSY -> warningPalette()
    CallOutcome.WRONG_NUMBER -> errorPalette()
}

/**
 * Thermometer palette for an INTERESTED client (HOW_IT_WORKS §4).
 * Cold = info blue, Warm = amber, Hot = error red — same brand colors
 * the rest of the app uses, so badges read consistently.
 */
@Composable
@ReadOnlyComposable
fun InterestLevel.palette(): StatusPalette = when (this) {
    InterestLevel.COLD -> infoPalette()
    InterestLevel.WARM -> warningPalette()
    InterestLevel.HOT -> errorPalette()
}

@Composable
@ReadOnlyComposable
fun InterestLevel.label(): String = when (this) {
    InterestLevel.COLD -> "Frío"
    InterestLevel.WARM -> "Tibio"
    InterestLevel.HOT -> "Caliente"
}

/** Single-glyph emoji shorthand for compact badges (lists / agenda). */
fun InterestLevel.emoji(): String = when (this) {
    InterestLevel.COLD -> "🟦"
    InterestLevel.WARM -> "🟧"
    InterestLevel.HOT -> "🟥"
}

@Composable
@ReadOnlyComposable
fun NoteType.palette(): StatusPalette = when (this) {
    NoteType.CALL -> infoPalette()
    NoteType.POST_CALL -> successPalette()
    NoteType.MANUAL -> neutralPalette()
    NoteType.FOLLOW_UP -> infoPalette()
}

@Composable
@ReadOnlyComposable
fun ClientStatus.label(): String = when (this) {
    ClientStatus.PENDING -> "Pending"
    ClientStatus.IN_PROGRESS -> "In progress"
    ClientStatus.INTERESTED -> "Interested"
    ClientStatus.REJECTED -> "Rejected"
    ClientStatus.UNREACHABLE -> "Unreachable"
    ClientStatus.CONVERTED -> "Converted"
    ClientStatus.DO_NOT_CALL -> "Do not call"
    ClientStatus.DISMISSED -> "Dismissed"
}

@Composable
@ReadOnlyComposable
fun CallOutcome.label(): String = when (this) {
    CallOutcome.ANSWERED_INTERESTED -> "Interested"
    CallOutcome.ANSWERED_NOT_INTERESTED -> "Not interested"
    CallOutcome.ANSWERED_OPT_OUT -> "Opt-out"
    CallOutcome.ANSWERED_SOLD -> "Sold"
    CallOutcome.NO_ANSWER -> "No answer"
    CallOutcome.BUSY -> "Busy"
    CallOutcome.WRONG_NUMBER -> "Wrong number"
}

/**
 * Distinctive icon for each [CallOutcome]. Used in the PostCall grid
 * so the agent recognizes the 7 buttons at a glance without having to
 * read the label every time. Matches the emoji set in
 * `calls-core/docs/HOW_IT_WORKS.md §5`.
 */
fun CallOutcome.icon(): ImageVector = when (this) {
    CallOutcome.ANSWERED_INTERESTED -> Icons.Filled.SentimentSatisfied   // 😊
    CallOutcome.ANSWERED_NOT_INTERESTED -> Icons.Filled.SentimentDissatisfied // 😐
    CallOutcome.ANSWERED_OPT_OUT -> Icons.Filled.Block                   // 🚫
    CallOutcome.ANSWERED_SOLD -> Icons.Filled.MonetizationOn             // 💰
    CallOutcome.NO_ANSWER -> Icons.Filled.PhoneMissed                    // 📵
    CallOutcome.BUSY -> Icons.Filled.RingVolume                          // 📞
    CallOutcome.WRONG_NUMBER -> Icons.Filled.QuestionMark                // ❓
}

/**
 * High-level grouping used by the PostCall UI: `ANSWERED_*` outcomes
 * live under "Respondió", the rest under "No respondió" (see HOW_IT_WORKS §5).
 */
val CallOutcome.isAnswered: Boolean
    get() = when (this) {
        CallOutcome.ANSWERED_INTERESTED,
        CallOutcome.ANSWERED_NOT_INTERESTED,
        CallOutcome.ANSWERED_OPT_OUT,
        CallOutcome.ANSWERED_SOLD -> true
        CallOutcome.NO_ANSWER,
        CallOutcome.BUSY,
        CallOutcome.WRONG_NUMBER -> false
    }

@Composable
@ReadOnlyComposable
fun NoteType.label(): String = when (this) {
    NoteType.CALL -> "During call"
    NoteType.POST_CALL -> "Post-call"
    NoteType.MANUAL -> "Manual"
    NoteType.FOLLOW_UP -> "Follow-up"
}

// ─────────────────────────────────────────────────────────────────────────────
// Palette helpers — pick the right brand color tied to the current theme.
// In light mode containers are the soft "100" tints; in dark mode we use a
// muted variant of the strong brand color so the chips don't glow.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@ReadOnlyComposable
private fun successPalette(): StatusPalette =
    if (isDark()) StatusPalette(Emerald600.copy(alpha = 0.25f), Emerald100)
    else StatusPalette(Emerald100, Emerald600)

@Composable
@ReadOnlyComposable
private fun warningPalette(): StatusPalette =
    if (isDark()) StatusPalette(Amber600.copy(alpha = 0.25f), Amber100)
    else StatusPalette(Amber100, Amber600)

@Composable
@ReadOnlyComposable
private fun errorPalette(): StatusPalette =
    if (isDark()) StatusPalette(Rose600.copy(alpha = 0.25f), Rose100)
    else StatusPalette(Rose100, Rose600)

@Composable
@ReadOnlyComposable
private fun infoPalette(): StatusPalette =
    if (isDark()) StatusPalette(Sky600.copy(alpha = 0.25f), Sky100)
    else StatusPalette(Sky100, Sky600)

@Composable
@ReadOnlyComposable
private fun neutralPalette(): StatusPalette =
    if (isDark()) StatusPalette(Slate600.copy(alpha = 0.30f), Slate200)
    else StatusPalette(Slate200, Slate700)

/**
 * Crude "is dark" check — relies on the surface luminance, which is reliable
 * in our theme since light surfaces are near-white and dark are near-black.
 */
@Composable
@ReadOnlyComposable
private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
