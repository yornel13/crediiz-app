package com.project.vortex.callsagent.presentation.onboarding

import android.Manifest
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

    /**
     * True when every *required* step is met. Optional steps (e.g.
     * Bluetooth) are intentionally excluded so a denied optional
     * permission never blocks entry to the app. Drives the "Continue"
     * button and the hard mandatory gate.
     */
    fun allMet(): Boolean =
        OnboardingStep.values().filter { it.required }.all { isStepMet(it) }

    /**
     * True when EVERY step is met, required and optional alike. Used to
     * decide whether the onboarding screen still has anything left to
     * offer the user (and thus whether to surface it on launch).
     */
    fun allGranted(): Boolean = OnboardingStep.values().all { isStepMet(it) }

    fun isStepMet(step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.RECORD_AUDIO ->
            hasPermission(Manifest.permission.RECORD_AUDIO)
        OnboardingStep.MODIFY_AUDIO_SETTINGS ->
            hasPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        OnboardingStep.NOTIFICATIONS -> hasNotificationPermission()
        OnboardingStep.BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored()
        OnboardingStep.BLUETOOTH_CONNECT -> hasBluetoothConnectPermission()
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true

    /**
     * BLUETOOTH_CONNECT is a runtime permission from API 31 (S). On the
     * API 30 floor it is an install-time permission (legacy BLUETOOTH),
     * so it is always considered "met" there.
     */
    fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else true

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

/**
 * One step per requirement. Order = display order in the onboarding screen.
 *
 * RECORD_AUDIO is the gate for SIP — without it Linphone cannot capture
 * the agent's voice and the call is one-way.
 *
 * [required] steps gate entry to the app ([allMet]); optional steps are
 * surfaced for convenience but never block the "Continue" button.
 */
enum class OnboardingStep(val required: Boolean = true) {
    RECORD_AUDIO,
    MODIFY_AUDIO_SETTINGS,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,

    /**
     * Bluetooth headset routing. Optional: the dialer works on the
     * speaker / wired headset without it, so a denied Bluetooth
     * permission must not lock the agent out of the app.
     */
    BLUETOOTH_CONNECT(required = false),
}
