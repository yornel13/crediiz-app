package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.domain.model.ClientDismissal
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Source of truth for agent-initiated client dismissals.
 *
 * The repository owns the dual-write pattern: a `dismiss(...)` call
 * inserts the audit event AND mutates the local `ClientEntity` so
 * the agent's UI updates immediately. Sync pushes the event later.
 */
interface ClientDismissalRepository {

    /**
     * Dismiss a client. Inserts a new `ClientDismissal` event,
     * updates the local client status to `DISMISSED`, cancels any
     * pending follow-up locally (server side will mirror on sync),
     * and triggers an immediate sync push.
     */
    suspend fun dismiss(
        clientId: String,
        reasonCode: DismissalReasonCode?,
        freeFormReason: String?,
    )

    /**
     * Reverse a dismissal that's still inside the 24 h recovery
     * window. Restores the client to `previousStatus`, marks the
     * event `undone`, and pushes the toggle on the next sync.
     *
     * Returns true if undo applied; false if there was no active
     * dismissal to undo.
     */
    suspend fun undo(clientId: String): Boolean

    /** Active dismissal for a client (no undo applied), if any. */
    suspend fun findActiveForClient(clientId: String): ClientDismissal?

    /** Stream of active dismissals inside the rolling window. */
    fun observeActiveSince(since: Instant): Flow<List<ClientDismissal>>

    /** Pending-sync count to feed the sync indicator. */
    fun observePendingCount(): Flow<Int>
}
