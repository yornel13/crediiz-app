package com.project.vortex.callsagent.presentation.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class OnboardingUiState(
    /** Granted state per step. Display order matches `OnboardingStep.values()`. */
    val statuses: Map<OnboardingStep, Boolean> = emptyMap(),
    /** Steps that the user previously rejected and that should now show
     * "Open Settings" instead of the regular request prompt. */
    val hardDenied: Set<OnboardingStep> = emptySet(),
) {
    val allMet: Boolean get() = statuses.values.all { it }
    fun isMet(step: OnboardingStep): Boolean = statuses[step] == true
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val gate: OnboardingGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshStatuses()
    }

    /** Re-checks every step. Called from the Activity's `onResume`. */
    fun refreshStatuses() {
        val statuses = OnboardingStep.values().associateWith { gate.isStepMet(it) }
        _uiState.value = _uiState.value.copy(statuses = statuses)
    }

    /** Marks a step as hard-denied so the screen swaps the action button to
     * "Open Settings". Activity calls this when a runtime request returns
     * denied AND `shouldShowRequestPermissionRationale` is false. */
    fun markHardDenied(step: OnboardingStep) {
        _uiState.value = _uiState.value.copy(
            hardDenied = _uiState.value.hardDenied + step,
        )
    }

    /** Clears the hard-denied flag once the user actually grants the
     * permission (e.g. via system Settings). Called automatically by
     * `refreshStatuses` when the step flips to met. */
    fun clearHardDenied(step: OnboardingStep) {
        if (step in _uiState.value.hardDenied) {
            _uiState.value = _uiState.value.copy(
                hardDenied = _uiState.value.hardDenied - step,
            )
        }
    }
}
