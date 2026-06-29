package com.project.vortex.callsagent.presentation.agenda

import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.domain.model.FollowUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Tests the local, Panama-time re-evaluation of a follow-up's agenda bucket
 * ([followUpAgendaSection] / [isFollowUpOverdue]) — the rule that lets the app
 * surface freshly-expired follow-ups without waiting for the next backend sync.
 */
class AgendaClassificationTest {

    private val zone: ZoneId = ZoneId.of("America/Panama")

    // "Now" = 2026-06-25 15:00 Panama.
    private val now: Instant = panama(2026, 6, 25, 15, 0)

    private fun panama(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant()

    private fun followUp(
        scheduledAt: Instant,
        status: FollowUpStatus = FollowUpStatus.PENDING,
    ) = FollowUp(
        mobileSyncId = "fu",
        clientId = "c",
        clientName = null,
        clientPhone = null,
        interactionMobileSyncId = null,
        scheduledAt = scheduledAt,
        reason = "",
        status = status,
        completedAt = null,
        deviceCreatedAt = Instant.EPOCH,
        syncStatus = SyncStatus.SYNCED,
        completionSyncStatus = SyncStatus.SYNCED,
    )

    private fun sectionOf(scheduledAt: Instant, status: FollowUpStatus = FollowUpStatus.PENDING) =
        followUpAgendaSection(followUp(scheduledAt, status), now, zone)

    @Test
    fun `pending later today is Programados (TODAY)`() {
        assertEquals(AgendaSection.TODAY, sectionOf(panama(2026, 6, 25, 16, 0)))
    }

    @Test
    fun `pending today within the 1h grace is still Programados`() {
        // 14:45 → only 15 min elapsed at 15:00, same day → not overdue.
        assertEquals(AgendaSection.TODAY, sectionOf(panama(2026, 6, 25, 14, 45)))
    }

    @Test
    fun `pending today more than 1h ago is Vencidos (OVERDUE)`() {
        // 13:30 → 1h30 elapsed at 15:00 → overdue.
        assertEquals(AgendaSection.OVERDUE, sectionOf(panama(2026, 6, 25, 13, 30)))
    }

    @Test
    fun `pending from a previous day is Vencidos even if under 1h-of-day`() {
        // Yesterday 23:30; the Panama day rolled over → overdue.
        assertEquals(AgendaSection.OVERDUE, sectionOf(panama(2026, 6, 24, 23, 30)))
    }

    @Test
    fun `pending tomorrow is Pendientes (UPCOMING)`() {
        assertEquals(AgendaSection.UPCOMING, sectionOf(panama(2026, 6, 26, 9, 0)))
    }

    @Test
    fun `backend EXPIRED is always Vencidos regardless of date`() {
        // Even a future-dated row marked EXPIRED by the backend is trusted.
        assertEquals(
            AgendaSection.OVERDUE,
            sectionOf(panama(2026, 6, 26, 9, 0), FollowUpStatus.EXPIRED),
        )
    }

    @Test
    fun `terminal states are never overdue`() {
        assertFalse(isFollowUpOverdue(followUp(panama(2026, 6, 24, 9, 0), FollowUpStatus.COMPLETED), now, zone))
        assertFalse(isFollowUpOverdue(followUp(panama(2026, 6, 24, 9, 0), FollowUpStatus.CANCELLED), now, zone))
    }

    @Test
    fun `buildAgendaSections renders sections in order and omits empty ones`() {
        val overdue = followUp(panama(2026, 6, 24, 9, 0)) // yesterday → OVERDUE
        val today = followUp(panama(2026, 6, 25, 16, 0)) // later today → TODAY
        val upcoming = followUp(panama(2026, 6, 27, 9, 0)) // future → UPCOMING
        // Pass them shuffled — output order must follow the section declaration.
        val map = buildAgendaSections(listOf(upcoming, today, overdue), emptyList(), now, zone)
        assertEquals(
            listOf(AgendaSection.OVERDUE, AgendaSection.TODAY, AgendaSection.UPCOMING),
            map.keys.toList(),
        )
    }

    @Test
    fun `buildAgendaSections omits sections that have no items`() {
        val upcoming = followUp(panama(2026, 6, 27, 9, 0))
        val map = buildAgendaSections(listOf(upcoming), emptyList(), now, zone)
        assertEquals(listOf(AgendaSection.UPCOMING), map.keys.toList())
    }

    @Test
    fun `overdue boundary - exactly 1h after is not yet overdue, just past is`() {
        // scheduled 14:00; +1h = 15:00 == now → NOT after → not overdue (same day).
        assertFalse(isFollowUpOverdue(followUp(panama(2026, 6, 25, 14, 0)), now, zone))
        // scheduled 13:59; +1h = 14:59 < now 15:00 → overdue.
        assertTrue(isFollowUpOverdue(followUp(panama(2026, 6, 25, 13, 59)), now, zone))
    }
}
