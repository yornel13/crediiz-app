package com.project.vortex.callsagent.domain.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reason a session ended unexpectedly. Drives the copy shown on the
 * login screen the next time the agent lands there.
 *
 * - [Invalidated]: backend reported `"Session has been invalidated"`
 *   on a 401 — single-active-session displaced this device, admin
 *   revoked it, or the agent logged out elsewhere.
 * - [Expired]: any other 401 — most commonly the JWT TTL elapsed.
 */
enum class SessionInvalidationReason {
    Invalidated,
    Expired,
}

/**
 * Single-source pub/sub for session-level events that need cross-cut
 * reactions (logout cleanup, navigation back to /login). Emitted by
 * the network layer (`SessionInvalidationInterceptor`); consumed by
 * the app shell (`MainActivity`).
 *
 * `extraBufferCapacity = 1` + `DROP_OLDEST` keeps the bus resilient
 * to bursts of 401s from parallel requests after a session dies —
 * we only need one notification to redirect.
 */
@Singleton
class SessionEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<SessionInvalidationReason>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SessionInvalidationReason> = _events.asSharedFlow()

    fun publish(reason: SessionInvalidationReason) {
        _events.tryEmit(reason)
    }
}
