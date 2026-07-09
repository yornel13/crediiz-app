package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.remote.dto.ActivityInteractionDto
import com.project.vortex.callsagent.data.remote.dto.ActivityNoteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hydration-mapper contract (`GET clients/{id}/activity` → Room entities).
 * Three rules protect the local cache:
 *  1. Server-born notes (null mobileSyncId) get a STABLE synthetic key so
 *     repeated hydrations converge instead of duplicating.
 *  2. Hydrated rows land SYNCED (never re-pushed) and interactions land
 *     confirmedByAgent=true (never fed to orphan-call recovery).
 *  3. Unknown enum values / bad dates from a newer backend drop the row
 *     (null) instead of throwing — hydration must never crash the app.
 */
class ClientActivityMapperTest {

    private fun noteDto(
        id: String = "665f1c2e9b3a4d0012345678",
        mobileSyncId: String? = "mob-1",
        type: String = "MANUAL",
        deviceCreatedAt: String? = "2026-07-01T10:00:00Z",
        createdAt: String = "2026-07-01T10:00:05Z",
    ) = ActivityNoteDto(
        id = id,
        mobileSyncId = mobileSyncId,
        clientId = "client-1",
        interactionMobileSyncId = null,
        content = "note body",
        type = type,
        authorId = "agent-1",
        authorName = "Ana",
        authorRole = "AGENT",
        deviceCreatedAt = deviceCreatedAt,
        createdAt = createdAt,
    )

    private fun interactionDto(
        mobileSyncId: String = "mob-int-1",
        outcome: String? = "NO_ANSWER",
        direction: String? = "OUTBOUND",
        callStartedAt: String? = "2026-07-01T09:00:00Z",
        callEndedAt: String? = "2026-07-01T09:01:30Z",
    ) = ActivityInteractionDto(
        id = "665f1c2e9b3a4d0087654321",
        mobileSyncId = mobileSyncId,
        clientId = "client-1",
        agentId = "agent-2",
        direction = direction,
        callStartedAt = callStartedAt,
        callEndedAt = callEndedAt,
        durationSeconds = 90,
        outcome = outcome,
        disconnectCause = null,
        deviceCreatedAt = "2026-07-01T09:01:31Z",
        createdAt = "2026-07-01T09:01:32Z",
    )

    // ── Rule 1: keys ──

    @Test
    fun `mobile-born note keeps its mobileSyncId as key`() {
        val entity = noteDto(mobileSyncId = "mob-1").toEntityOrNull()
        assertNotNull(entity)
        assertEquals("mob-1", entity!!.mobileSyncId)
    }

    @Test
    fun `server-born note derives a stable synthetic key from the server id`() {
        val first = noteDto(mobileSyncId = null).toEntityOrNull()
        val second = noteDto(mobileSyncId = null).toEntityOrNull()
        assertNotNull(first)
        assertEquals("srv-665f1c2e9b3a4d0012345678", first!!.mobileSyncId)
        // Same server row → same key on every hydration (no duplicates).
        assertEquals(first.mobileSyncId, second!!.mobileSyncId)
    }

    // ── Rule 2: hydrated rows are settled history ──

    @Test
    fun `hydrated note lands SYNCED so it is never re-pushed`() {
        assertEquals(SyncStatus.SYNCED, noteDto().toEntityOrNull()!!.syncStatus)
    }

    @Test
    fun `hydrated note preserves the server author snapshot`() {
        // N:M assignment: the timeline must attribute other agents' notes.
        val entity = noteDto().toEntityOrNull()!!
        assertEquals("agent-1", entity.authorId)
        assertEquals("Ana", entity.authorName)
        assertEquals("AGENT", entity.authorRole)
    }

    @Test
    fun `hydrated interaction is SYNCED and agent-confirmed`() {
        val entity = interactionDto().toEntityOrNull()
        assertNotNull(entity)
        assertEquals(SyncStatus.SYNCED, entity!!.syncStatus)
        // False would feed hydrated calls into orphan-call recovery.
        assertTrue(entity.confirmedByAgent)
    }

    // ── Rule 3: defensive parsing ──

    @Test
    fun `unknown note type from a newer backend drops the row`() {
        assertNull(noteDto(type = "VOICE_MEMO").toEntityOrNull())
    }

    @Test
    fun `known note types map through`() {
        assertEquals(NoteType.DISMISSAL, noteDto(type = "DISMISSAL").toEntityOrNull()!!.type)
        assertEquals(NoteType.FOLLOW_UP, noteDto(type = "FOLLOW_UP").toEntityOrNull()!!.type)
    }

    @Test
    fun `note with unparseable dates drops the row`() {
        assertNull(noteDto(deviceCreatedAt = "not-a-date", createdAt = "also-bad").toEntityOrNull())
    }

    @Test
    fun `note falls back to server createdAt when device timestamp is missing`() {
        val entity = noteDto(deviceCreatedAt = null).toEntityOrNull()
        assertNotNull(entity)
        assertEquals("2026-07-01T10:00:05Z", entity!!.deviceCreatedAt.toString())
    }

    @Test
    fun `interaction without outcome drops the row`() {
        assertNull(interactionDto(outcome = null).toEntityOrNull())
        assertNull(interactionDto(outcome = "TELEPATHY").toEntityOrNull())
    }

    @Test
    fun `interaction with unknown direction defaults to OUTBOUND`() {
        assertEquals(
            CallDirection.OUTBOUND,
            interactionDto(direction = "CARRIER_PIGEON").toEntityOrNull()!!.direction,
        )
    }

    @Test
    fun `interaction missing callEndedAt falls back to callStartedAt`() {
        val entity = interactionDto(callEndedAt = null).toEntityOrNull()
        assertNotNull(entity)
        assertEquals(entity!!.callStartedAt, entity.callEndedAt)
    }

    @Test
    fun `outcome maps through for known values`() {
        assertEquals(CallOutcome.NO_ANSWER, interactionDto().toEntityOrNull()!!.outcome)
    }
}
