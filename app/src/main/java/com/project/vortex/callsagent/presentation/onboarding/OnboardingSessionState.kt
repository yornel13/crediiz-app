package com.project.vortex.callsagent.presentation.onboarding

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime flag tracking whether the user has already completed
 * or explicitly skipped onboarding in THIS launch.
 *
 * Optional permissions (e.g. Bluetooth) don't block entry, but the
 * onboarding screen is still surfaced on every cold start while they're
 * missing. This flag prevents an immediate re-bounce right after the
 * user taps "Continue" — without it the navigation would loop forever:
 * MainActivity → onboarding → continue → MainActivity → onboarding → …
 *
 * It is intentionally NOT persisted: a fresh process resets it to
 * `false`, so a new app launch offers the optional permission again.
 */
@Singleton
class OnboardingSessionState @Inject constructor() {
    @Volatile
    var dismissedThisSession: Boolean = false
}
