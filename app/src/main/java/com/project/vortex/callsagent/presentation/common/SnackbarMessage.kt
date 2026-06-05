package com.project.vortex.callsagent.presentation.common

import androidx.annotation.StringRes

/**
 * UI-agnostic snackbar payload emitted by ViewModels via a Channel.
 * The screen collects the channel, resolves [textRes] against the
 * Activity's locale-overridden Context, and forwards the string to its
 * `SnackbarHostState`.
 *
 * ViewModels carry NO resolved strings: a ViewModel has no access to
 * the locale-aware Context, so it can only describe *which* message to
 * show (a [StringRes]) plus any positional format [args]. The UI layer
 * resolves it via `context.getString(textRes, *args.toTypedArray())`.
 *
 * Kept deliberately tiny — no SnackbarDuration, no action label. Add
 * those only when a real use-case shows up; today every error is a
 * "show short, let it auto-dismiss".
 */
data class SnackbarMessage(
    @StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    val tone: Tone = Tone.INFO,
) {
    enum class Tone { INFO, SUCCESS, WARN, ERROR }
}
