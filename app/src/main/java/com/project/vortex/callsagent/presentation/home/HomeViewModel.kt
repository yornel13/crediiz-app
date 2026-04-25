package com.project.vortex.callsagent.presentation.home

import androidx.lifecycle.ViewModel
import com.project.vortex.callsagent.data.sync.LoginHydrationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val loginHydrationState: LoginHydrationState,
) : ViewModel() {

    val isStaleData: StateFlow<Boolean> = loginHydrationState.isStale

    fun dismissStaleBanner() = loginHydrationState.dismiss()
}
