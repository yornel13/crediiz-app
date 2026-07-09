package com.project.vortex.callsagent.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract tests for [SyncIdentityPolicy] — the gate that prevents a
 * WorkManager sync (which can outlive a logout and straddle a login) from
 * pushing one agent's PENDING rows under another agent's JWT. The server
 * attributes authorship from the token, so a wrong verdict here corrupts
 * the append-only audit trail silently and irreversibly.
 */
class SyncIdentityPolicyTest {

    @Test
    fun `same session and owner is consistent`() {
        assertEquals(
            IdentityCheck.CONSISTENT,
            SyncIdentityPolicy.evaluate(sessionAgentId = "agent-1", dataOwnerAgentId = "agent-1"),
        )
    }

    @Test
    fun `no session blocks the push`() {
        // Logged out — a retrying worker must never push with a stale/absent token.
        assertEquals(
            IdentityCheck.BLOCKED,
            SyncIdentityPolicy.evaluate(sessionAgentId = null, dataOwnerAgentId = "agent-1"),
        )
    }

    @Test
    fun `blank session blocks the push`() {
        assertEquals(
            IdentityCheck.BLOCKED,
            SyncIdentityPolicy.evaluate(sessionAgentId = "", dataOwnerAgentId = "agent-1"),
        )
    }

    @Test
    fun `different owner blocks the push`() {
        // Agent B logged in while agent A's rows are still on device.
        assertEquals(
            IdentityCheck.BLOCKED,
            SyncIdentityPolicy.evaluate(sessionAgentId = "agent-2", dataOwnerAgentId = "agent-1"),
        )
    }

    @Test
    fun `null owner with active session adopts`() {
        // Pre-ownership-tracking upgrade: rows were made by the active agent.
        assertEquals(
            IdentityCheck.ADOPT_SESSION,
            SyncIdentityPolicy.evaluate(sessionAgentId = "agent-1", dataOwnerAgentId = null),
        )
    }

    @Test
    fun `null owner without session still blocks`() {
        // BLOCKED must win over ADOPT_SESSION when nobody is logged in.
        assertEquals(
            IdentityCheck.BLOCKED,
            SyncIdentityPolicy.evaluate(sessionAgentId = null, dataOwnerAgentId = null),
        )
    }
}
