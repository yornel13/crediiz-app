package com.project.vortex.callsagent.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory session signal to flag that the agent logged in but the
 * post-login refresh failed (e.g. offline). UI surfaces a dismissable
 * "data may be stale" banner when [isStale] is true. Cleared on dismiss
 * or on logout.
 */
@Singleton
class LoginHydrationState @Inject constructor() {

    private val _isStale = MutableStateFlow(false)
    val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

    fun markStale() {
        _isStale.value = true
    }

    fun markFresh() {
        _isStale.value = false
    }

    fun dismiss() {
        _isStale.value = false
    }
}
