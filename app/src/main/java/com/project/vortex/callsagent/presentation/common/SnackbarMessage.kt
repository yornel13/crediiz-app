package com.project.vortex.callsagent.presentation.common

/**
 * UI-agnostic snackbar payload emitted by ViewModels via a Channel.
 * The screen collects the channel and forwards to its
 * `SnackbarHostState`.
 *
 * Kept deliberately tiny — no SnackbarDuration, no action label. Add
 * those only when a real use-case shows up; today every error is a
 * "show short, let it auto-dismiss".
 */
data class SnackbarMessage(
    val text: String,
    val tone: Tone = Tone.INFO,
) {
    enum class Tone { INFO, SUCCESS, WARN, ERROR }
}
