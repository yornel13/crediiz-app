package com.project.vortex.callsagent.presentation.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless checks for the mandatory onboarding requirements. The Activity
 * uses these on every `onResume` to decide if the user can proceed; the
 * `MainActivity` uses `allMet()` to decide whether to redirect on launch.
 *
 * Adding a new requirement: extend `requirementsInOrder()` AND
 * `OnboardingStep`. Both must stay aligned 1:1 — the screen renders one
 * card per step.
 */
@Singleton
class OnboardingGate @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun allMet(): Boolean = OnboardingStep.values().all { isStepMet(it) }

    fun isStepMet(step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.DIALER_ROLE -> isDialerRoleHeld()
        OnboardingStep.CALL_PHONE -> hasPermission(Manifest.permission.CALL_PHONE)
        OnboardingStep.ANSWER_PHONE_CALLS ->
            hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)
        OnboardingStep.MODIFY_AUDIO_SETTINGS ->
            hasPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        OnboardingStep.NOTIFICATIONS -> hasNotificationPermission()
        OnboardingStep.BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored()
    }

    fun isDialerRoleHeld(): Boolean {
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

/**
 * One step per requirement. Order = display order in the onboarding screen.
 *
 * Dialer role goes first because the others (especially audio routing in
 * Phase 3.2) won't behave correctly until we hold the role.
 */
enum class OnboardingStep {
    DIALER_ROLE,
    CALL_PHONE,
    ANSWER_PHONE_CALLS,
    MODIFY_AUDIO_SETTINGS,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
}
