package com.project.vortex.callsagent.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.RemovalReason

/**
 * A semantic color pair (background + foreground) used for badges, chips,
 * and status indicators. Both colors are picked to remain readable on the
 * current Material color scheme (light or dark).
 */
data class StatusPalette(
    val container: Color,
    val onContainer: Color,
)

/**
 * Funnel progression colors: neutral → amber → blue → green, with red for
 * the off-funnel REMOVED state. The gradient lets the agent read "how far"
 * a client is at a glance.
 */
@Composable
@ReadOnlyComposable
fun ClientStatus.palette(): StatusPalette = when (this) {
    ClientStatus.PENDING -> neutralPalette()
    ClientStatus.INTERESTED -> warningPalette()
    ClientStatus.CITED -> infoPalette()
    ClientStatus.CONVERTED -> successPalette()
    ClientStatus.REMOVED -> errorPalette()
}

@Composable
@ReadOnlyComposable
fun CallOutcome.palette(): StatusPalette = when (this) {
    CallOutcome.INTERESTED -> successPalette()
    CallOutcome.SCHEDULED -> infoPalette()
    CallOutcome.SOLD -> successPalette()
    CallOutcome.NO_ANSWER,
    CallOutcome.BUSY,
    CallOutcome.OUT_OF_SERVICE,
    CallOutcome.VOICEMAIL -> warningPalette()
    CallOutcome.NOT_INTERESTED,
    CallOutcome.DO_NOT_CALL,
    CallOutcome.WRONG_NUMBER,
    CallOutcome.HAS_LOAN,
    CallOutcome.DECEASED,
    CallOutcome.NOT_APPLICABLE -> errorPalette()
    // Answered-but-unclassified placeholder — neutral so it reads as
    // "pending", not as a contact or removal result.
    CallOutcome.NO_SELECTED -> neutralPalette()
}

@Composable
@ReadOnlyComposable
fun NoteType.palette(): StatusPalette = when (this) {
    NoteType.CALL -> infoPalette()
    NoteType.POST_CALL -> successPalette()
    NoteType.MANUAL -> neutralPalette()
    NoteType.FOLLOW_UP -> infoPalette()
    // Server-side auto-Notes — mobile currently does not render these
    // (the local Note feed is agent-authored only) but the enum needs
    // exhaustive coverage. Fall back to the neutral palette so any
    // unexpected render is at least readable.
    NoteType.STATUS_CHANGE -> neutralPalette()
    NoteType.DISMISSAL -> neutralPalette()
}

@Composable
@ReadOnlyComposable
fun ClientStatus.label(): String = when (this) {
    ClientStatus.PENDING -> stringResource(R.string.enum_status_pending)
    ClientStatus.INTERESTED -> stringResource(R.string.enum_status_interested)
    ClientStatus.CITED -> stringResource(R.string.enum_status_cited)
    ClientStatus.CONVERTED -> stringResource(R.string.enum_status_converted)
    ClientStatus.REMOVED -> stringResource(R.string.enum_status_removed)
}

@Composable
@ReadOnlyComposable
fun CallOutcome.label(): String = when (this) {
    CallOutcome.NO_ANSWER -> stringResource(R.string.enum_outcome_no_answer)
    CallOutcome.BUSY -> stringResource(R.string.enum_outcome_busy)
    CallOutcome.OUT_OF_SERVICE -> stringResource(R.string.enum_outcome_out_of_service)
    CallOutcome.VOICEMAIL -> stringResource(R.string.enum_outcome_voicemail)
    CallOutcome.INTERESTED -> stringResource(R.string.enum_outcome_interested)
    CallOutcome.SCHEDULED -> stringResource(R.string.enum_outcome_scheduled)
    CallOutcome.SOLD -> stringResource(R.string.enum_outcome_sold)
    CallOutcome.NOT_INTERESTED -> stringResource(R.string.enum_outcome_not_interested)
    CallOutcome.DO_NOT_CALL -> stringResource(R.string.enum_outcome_do_not_call)
    CallOutcome.WRONG_NUMBER -> stringResource(R.string.enum_outcome_wrong_number)
    CallOutcome.HAS_LOAN -> stringResource(R.string.enum_outcome_has_loan)
    CallOutcome.DECEASED -> stringResource(R.string.enum_outcome_deceased)
    CallOutcome.NOT_APPLICABLE -> stringResource(R.string.enum_outcome_not_applicable)
    CallOutcome.NO_SELECTED -> stringResource(R.string.enum_outcome_no_selected)
}

@Composable
@ReadOnlyComposable
fun RemovalReason.label(): String = when (this) {
    RemovalReason.NOT_INTERESTED -> stringResource(R.string.enum_removal_not_interested)
    RemovalReason.UNREACHABLE -> stringResource(R.string.enum_removal_unreachable)
    RemovalReason.DO_NOT_CALL -> stringResource(R.string.enum_removal_do_not_call)
    RemovalReason.WRONG_NUMBER -> stringResource(R.string.enum_removal_wrong_number)
    RemovalReason.HAS_LOAN -> stringResource(R.string.enum_removal_has_loan)
    RemovalReason.DECEASED -> stringResource(R.string.enum_removal_deceased)
    RemovalReason.NOT_APPLICABLE -> stringResource(R.string.enum_removal_not_applicable)
    RemovalReason.OTHER -> stringResource(R.string.enum_removal_other)
}

/**
 * Distinctive icon for each [CallOutcome]. Used in the PostCall grid so
 * the agent recognizes the buttons at a glance without reading the label.
 */
fun CallOutcome.icon(): ImageVector = when (this) {
    CallOutcome.NO_ANSWER -> Icons.Filled.PhoneMissed
    CallOutcome.BUSY -> Icons.Filled.RingVolume
    CallOutcome.OUT_OF_SERVICE -> Icons.Filled.PhoneDisabled
    CallOutcome.VOICEMAIL -> Icons.Filled.Voicemail
    CallOutcome.INTERESTED -> Icons.Filled.SentimentSatisfied
    CallOutcome.SCHEDULED -> Icons.Filled.Event
    CallOutcome.SOLD -> Icons.Filled.MonetizationOn
    CallOutcome.NOT_INTERESTED -> Icons.Filled.SentimentDissatisfied
    CallOutcome.DO_NOT_CALL -> Icons.Filled.Block
    CallOutcome.WRONG_NUMBER -> Icons.Filled.QuestionMark
    CallOutcome.HAS_LOAN -> Icons.Filled.AccountBalance
    CallOutcome.DECEASED -> Icons.Filled.PersonOff
    CallOutcome.NOT_APPLICABLE -> Icons.Filled.RemoveCircleOutline
    CallOutcome.NO_SELECTED -> Icons.Filled.Pending
}

/**
 * High-level grouping used by the PostCall UI: outcomes that imply the
 * agent actually talked to (or reached) the target live under "Respondió",
 * the no-contact ones under "No respondió".
 */
val CallOutcome.isAnswered: Boolean
    get() = when (this) {
        CallOutcome.INTERESTED,
        CallOutcome.SCHEDULED,
        CallOutcome.SOLD,
        CallOutcome.NOT_INTERESTED,
        CallOutcome.DO_NOT_CALL,
        CallOutcome.HAS_LOAN,
        CallOutcome.DECEASED,
        CallOutcome.NOT_APPLICABLE,
        // Unclassified placeholder still implies the call WAS answered —
        // group it under "answered" so the activity log reads correctly.
        CallOutcome.NO_SELECTED -> true
        CallOutcome.NO_ANSWER,
        CallOutcome.BUSY,
        CallOutcome.OUT_OF_SERVICE,
        CallOutcome.VOICEMAIL,
        CallOutcome.WRONG_NUMBER -> false
    }

@Composable
@ReadOnlyComposable
fun NoteType.label(): String = when (this) {
    NoteType.CALL -> stringResource(R.string.enum_note_call)
    NoteType.POST_CALL -> stringResource(R.string.enum_note_post_call)
    NoteType.MANUAL -> stringResource(R.string.enum_note_manual)
    NoteType.FOLLOW_UP -> stringResource(R.string.enum_note_follow_up)
    // Server-side auto-Notes — see palette() above.
    NoteType.STATUS_CHANGE -> stringResource(R.string.enum_note_status_change)
    NoteType.DISMISSAL -> stringResource(R.string.enum_note_dismissal)
}

// ─────────────────────────────────────────────────────────────────────────────
// Palette helpers — pick the right brand color tied to the current theme.
// In light mode containers are the soft "100" tints; in dark mode we use a
// muted variant of the strong brand color so the chips don't glow.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@ReadOnlyComposable
internal fun successPalette(): StatusPalette =
    if (isDark()) StatusPalette(Emerald600.copy(alpha = 0.25f), Emerald100)
    else StatusPalette(Emerald100, Emerald600)

@Composable
@ReadOnlyComposable
private fun warningPalette(): StatusPalette =
    if (isDark()) StatusPalette(Amber600.copy(alpha = 0.25f), Amber100)
    else StatusPalette(Amber100, Amber600)

@Composable
@ReadOnlyComposable
internal fun errorPalette(): StatusPalette =
    if (isDark()) StatusPalette(Rose600.copy(alpha = 0.25f), Rose100)
    else StatusPalette(Rose100, Rose600)

@Composable
@ReadOnlyComposable
internal fun infoPalette(): StatusPalette =
    if (isDark()) StatusPalette(Sky600.copy(alpha = 0.25f), Sky100)
    else StatusPalette(Sky100, Sky600)

@Composable
@ReadOnlyComposable
internal fun neutralPalette(): StatusPalette =
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
