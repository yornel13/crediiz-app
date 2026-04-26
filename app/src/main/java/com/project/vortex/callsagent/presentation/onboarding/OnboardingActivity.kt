package com.project.vortex.callsagent.presentation.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project.vortex.callsagent.MainActivity
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mandatory onboarding gate. The user lands here whenever any of the dialer
 * requirements are missing, and cannot leave (back button blocked, recents
 * excluded) until all are granted.
 *
 * On every `onResume` we re-check via [OnboardingGate]; if the user toggled
 * something in system Settings while we were paused, the UI updates without
 * any explicit action.
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    @Inject lateinit var gate: OnboardingGate

    private val viewModel: OnboardingViewModel by viewModels()

    private val singlePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The Activity's onResume will re-check status. If the user denied
            // with "Don't ask again" we set the hard-denied flag here.
            currentRequest?.let { step ->
                if (!it && !shouldShowRationale(step)) {
                    viewModel.markHardDenied(step)
                }
                currentRequest = null
            }
            viewModel.refreshStatuses()
        }

    private val genericIntentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshStatuses()
        }

    /** Tracks which step kicked off the most recent permission request so
     * we know which one to flag as hard-denied if it gets refused. */
    private var currentRequest: OnboardingStep? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Block back navigation — the user must complete onboarding to use the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // No-op. The hardware/gesture back is intentionally a dead end here.
            }
        })

        setContent {
            CallsAgendsTheme {
                OnboardingScreen(
                    viewModel = viewModel,
                    onAction = ::handleStepAction,
                    onAllMetContinue = ::completeAndLaunchHome,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshStatuses()
                if (viewModel.uiState.value.allMet) completeAndLaunchHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatuses()
        // If the user came back from system Settings having flipped a hard-denied
        // permission, clear the flag so the regular request flow returns.
        OnboardingStep.values().forEach {
            if (gate.isStepMet(it)) viewModel.clearHardDenied(it)
        }
        if (viewModel.uiState.value.allMet) completeAndLaunchHome()
    }

    private fun handleStepAction(step: OnboardingStep) {
        if (step in viewModel.uiState.value.hardDenied) {
            openAppSettings()
            return
        }
        when (step) {
            OnboardingStep.DIALER_ROLE -> requestDialerRole()
            OnboardingStep.CALL_PHONE -> requestPermission(step, Manifest.permission.CALL_PHONE)
            OnboardingStep.ANSWER_PHONE_CALLS ->
                requestPermission(step, Manifest.permission.ANSWER_PHONE_CALLS)
            OnboardingStep.MODIFY_AUDIO_SETTINGS ->
                requestPermission(step, Manifest.permission.MODIFY_AUDIO_SETTINGS)
            OnboardingStep.NOTIFICATIONS -> requestNotifications(step)
            OnboardingStep.BATTERY_OPTIMIZATION -> requestBatteryWhitelist()
        }
    }

    private fun requestPermission(step: OnboardingStep, permission: String) {
        currentRequest = step
        singlePermissionLauncher.launch(permission)
    }

    private fun requestNotifications(step: OnboardingStep) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission(step, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-API 33: notifications are granted by default.
            viewModel.refreshStatuses()
        }
    }

    private fun requestDialerRole() {
        val rm = getSystemService(RoleManager::class.java) ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            genericIntentLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            viewModel.refreshStatuses()
        }
    }

    @Suppress("BatteryLife") // Justification: enterprise dialer; agents need
    // background reliability for an entire shift. Not Play-store distributed.
    private fun requestBatteryWhitelist() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { genericIntentLauncher.launch(intent) }
            .recoverCatching {
                // Fallback to the generic battery settings page if the targeted
                // intent isn't supported on this OEM (rare).
                genericIntentLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        genericIntentLauncher.launch(intent)
    }

    private fun shouldShowRationale(step: OnboardingStep): Boolean {
        val perm = when (step) {
            OnboardingStep.CALL_PHONE -> Manifest.permission.CALL_PHONE
            OnboardingStep.ANSWER_PHONE_CALLS -> Manifest.permission.ANSWER_PHONE_CALLS
            OnboardingStep.MODIFY_AUDIO_SETTINGS -> Manifest.permission.MODIFY_AUDIO_SETTINGS
            OnboardingStep.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS
                else return true
            OnboardingStep.DIALER_ROLE,
            OnboardingStep.BATTERY_OPTIMIZATION -> return true
        }
        return ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
    }

    private var navigatedHome = false
    private fun completeAndLaunchHome() {
        if (navigatedHome) return
        navigatedHome = true
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
