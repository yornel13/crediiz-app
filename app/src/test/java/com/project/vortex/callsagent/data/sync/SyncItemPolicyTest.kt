package com.project.vortex.callsagent.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [SyncItemPolicy.isPermanentFailure]. A false negative
 * leaves an unsyncable row in the outbox forever (re-pushed on every sync);
 * a false positive drops a row that could still have synced.
 */
class SyncItemPolicyTest {

    @Test
    fun `deleted client is permanent`() {
        // Nest's default message for ClientNotFoundException.
        assertTrue(SyncItemPolicy.isPermanentFailure("Client Not Found Exception"))
        assertTrue(SyncItemPolicy.isPermanentFailure("Client with id 66f0 not found."))
    }

    @Test
    fun `missing or already completed follow-up is permanent`() {
        assertTrue(SyncItemPolicy.isPermanentFailure("Follow-up not found or already completed"))
    }

    @Test
    fun `unknown or transient errors are retried`() {
        assertFalse(SyncItemPolicy.isPermanentFailure(null))
        assertFalse(SyncItemPolicy.isPermanentFailure("Unknown error"))
        assertFalse(SyncItemPolicy.isPermanentFailure("connection pool timeout"))
        assertFalse(SyncItemPolicy.isPermanentFailure("Agent not found"))
    }
}
