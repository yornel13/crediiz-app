package com.project.vortex.callsagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
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
    ClientStatus.INTERESTED -> successPalette()
    ClientStatus.REJECTED -> errorPalette()
    ClientStatus.INVALID_NUMBER -> errorPalette()
    ClientStatus.CONVERTED -> infoPalette()
    ClientStatus.DO_NOT_CALL -> errorPalette()
    ClientStatus.DISMISSED -> neutralPalette()
}

@Composable
@ReadOnlyComposable
fun CallOutcome.palette(): StatusPalette = when (this) {
    CallOutcome.INTERESTED -> successPalette()
    CallOutcome.NOT_INTERESTED -> errorPalette()
    CallOutcome.NO_ANSWER -> warningPalette()
    CallOutcome.BUSY -> warningPalette()
    CallOutcome.INVALID_NUMBER -> errorPalette()
    CallOutcome.SOLD -> infoPalette()
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
    ClientStatus.INTERESTED -> "Interested"
    ClientStatus.REJECTED -> "Rejected"
    ClientStatus.INVALID_NUMBER -> "Invalid number"
    ClientStatus.CONVERTED -> "Sold"
    ClientStatus.DO_NOT_CALL -> "Do not call"
    ClientStatus.DISMISSED -> "Dismissed"
}

@Composable
@ReadOnlyComposable
fun CallOutcome.label(): String = when (this) {
    CallOutcome.INTERESTED -> "Interested"
    CallOutcome.NOT_INTERESTED -> "Not interested"
    CallOutcome.NO_ANSWER -> "No answer"
    CallOutcome.BUSY -> "Busy"
    CallOutcome.INVALID_NUMBER -> "Invalid number"
    CallOutcome.SOLD -> "Sold"
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
