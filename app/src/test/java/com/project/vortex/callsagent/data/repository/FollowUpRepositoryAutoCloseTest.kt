package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.FollowUpDao
import com.project.vortex.callsagent.data.local.entity.FollowUpEntity
import com.project.vortex.callsagent.data.remote.api.FollowUpsApi
import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.FollowUpResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Regression suite for the auto-close policy introduced in
 * `FollowUpRepository.markPendingForClientCompleted`. The policy:
 *
 *  - A call that just finished closes every PENDING follow-up of the
 *    same client whose `scheduledAt <= asOf`.
 *  - Future-dated follow-ups for the same client are preserved.
 *  - Follow-ups of OTHER clients are not affected.
 *  - Already-COMPLETED rows are not re-touched (idempotent).
 *
 * Without this policy the v1.0 flow accumulated past-due PENDING rows
 * indefinitely whenever the outcome wasn't ANSWERED_INTERESTED with
 * `replacePending=true` — see SECURITY_DEBT / agenda follow-up audit.
 */
class FollowUpRepositoryAutoCloseTest {

    private val dao = FakeFollowUpDao()
    private val api = NoopFollowUpsApi()
    private val repo: FollowUpRepositoryImpl
        get() = FollowUpRepositoryImpl(api, dao)

    private val asOf: Instant = Instant.parse("2026-05-23T15:00:00Z")
    private val past: Instant = asOf.minus(2, ChronoUnit.HOURS)
    private val future: Instant = asOf.plus(2, ChronoUnit.HOURS)

    @Test
    fun `closes a single past-due pending follow-up for the client`() = runBlocking {
        dao.seed(pending(id = "fu-1", clientId = "c-1", scheduledAt = past))

        val closed = repo.markPendingForClientCompleted("c-1", asOf)

        assertEquals(1, closed)
        val row = dao.findById("fu-1")!!
        assertEquals(FollowUpStatus.COMPLETED, row.status)
        assertEquals(asOf, row.completedAt)
        assertEquals(SyncStatus.PENDING, row.completionSyncStatus)
    }

    @Test
    fun `closes multiple past-due pending rows of the same client in one shot`() = runBlocking {
        dao.seed(pending(id = "fu-1", clientId = "c-1", scheduledAt = past))
        dao.seed(
            pending(
                id = "fu-2",
                clientId = "c-1",
                scheduledAt = past.minus(1, ChronoUnit.DAYS),
            ),
        )

        val closed = repo.markPendingForClientCompleted("c-1", asOf)

        assertEquals(2, closed)
        assertEquals(FollowUpStatus.COMPLETED, dao.findById("fu-1")!!.status)
        assertEquals(FollowUpStatus.COMPLETED, dao.findById("fu-2")!!.status)
    }

    @Test
    fun `preserves a future-dated pending follow-up for the same client`() = runBlocking {
        dao.seed(pending(id = "past", clientId = "c-1", scheduledAt = past))
        dao.seed(pending(id = "future", clientId = "c-1", scheduledAt = future))

        val closed = repo.markPendingForClientCompleted("c-1", asOf)

        assertEquals(1, closed)
        assertEquals(FollowUpStatus.COMPLETED, dao.findById("past")!!.status)
        // Future row untouched — different intent ("llamar mañana").
        val futureRow = dao.findById("future")!!
        assertEquals(FollowUpStatus.PENDING, futureRow.status)
        assertNull(futureRow.completedAt)
    }

    @Test
    fun `does not touch follow-ups belonging to other clients`() = runBlocking {
        dao.seed(pending(id = "mine", clientId = "c-1", scheduledAt = past))
        dao.seed(pending(id = "other", clientId = "c-2", scheduledAt = past))

        val closed = repo.markPendingForClientCompleted("c-1", asOf)

        assertEquals(1, closed)
        assertEquals(FollowUpStatus.COMPLETED, dao.findById("mine")!!.status)
        assertEquals(FollowUpStatus.PENDING, dao.findById("other")!!.status)
    }

    @Test
    fun `is idempotent — second call returns 0 and does not re-stamp completedAt`() =
        runBlocking {
            dao.seed(pending(id = "fu-1", clientId = "c-1", scheduledAt = past))

            val first = repo.markPendingForClientCompleted("c-1", asOf)
            assertEquals(1, first)
            val firstStamp = dao.findById("fu-1")!!.completedAt

            val later = asOf.plus(10, ChronoUnit.MINUTES)
            val second = repo.markPendingForClientCompleted("c-1", later)

            assertEquals(0, second)
            // completedAt must remain the original timestamp, not get
            // overwritten on every subsequent save.
            assertEquals(firstStamp, dao.findById("fu-1")!!.completedAt)
        }

    @Test
    fun `returns 0 when the client has no pending follow-ups at all`() = runBlocking {
        val closed = repo.markPendingForClientCompleted("c-1", asOf)
        assertEquals(0, closed)
    }

    @Test
    fun `does not touch already-COMPLETED rows`() = runBlocking {
        val already = pending(id = "done", clientId = "c-1", scheduledAt = past).copy(
            status = FollowUpStatus.COMPLETED,
            completedAt = past,
            completionSyncStatus = SyncStatus.SYNCED,
        )
        dao.seed(already)

        val closed = repo.markPendingForClientCompleted("c-1", asOf)

        assertEquals(0, closed)
        // completionSyncStatus stayed SYNCED — we didn't bump it back to PENDING.
        assertEquals(SyncStatus.SYNCED, dao.findById("done")!!.completionSyncStatus)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun pending(id: String, clientId: String, scheduledAt: Instant) =
        FollowUpEntity(
            mobileSyncId = id,
            clientId = clientId,
            interactionMobileSyncId = null,
            scheduledAt = scheduledAt,
            reason = "",
            status = FollowUpStatus.PENDING,
            completedAt = null,
            cancelledAt = null,
            cancelReason = null,
            deviceCreatedAt = scheduledAt,
            syncStatus = SyncStatus.SYNCED,
            completionSyncStatus = SyncStatus.SYNCED,
            clientName = "Client $clientId",
            clientPhone = "+507 1234-5678",
        )
}

/**
 * In-memory `FollowUpDao` covering only the methods exercised by the
 * auto-close policy. Other operations throw — failing loud beats silent
 * test rot if the policy ever depends on a new DAO call.
 */
private class FakeFollowUpDao : FollowUpDao {

    private val rows = mutableMapOf<String, FollowUpEntity>()

    fun seed(row: FollowUpEntity) {
        rows[row.mobileSyncId] = row
    }

    override suspend fun findById(id: String): FollowUpEntity? = rows[id]

    override suspend fun markPendingCompletedForClient(clientId: String, asOf: Instant): Int {
        var n = 0
        rows.forEach { (id, row) ->
            if (row.clientId == clientId &&
                row.status == FollowUpStatus.PENDING &&
                !row.scheduledAt.isAfter(asOf)
            ) {
                rows[id] = row.copy(
                    status = FollowUpStatus.COMPLETED,
                    completedAt = asOf,
                    completionSyncStatus = SyncStatus.PENDING,
                )
                n++
            }
        }
        return n
    }

    // ── Unused by this suite — fail loud if a future change leans on them ─

    override suspend fun insert(followUp: FollowUpEntity) = error("not stubbed")
    override suspend fun upsertAll(followUps: List<FollowUpEntity>) = error("not stubbed")
    override fun observePending(status: FollowUpStatus, from: Instant): Flow<List<FollowUpEntity>> =
        error("not stubbed")
    override fun observeNextPendingForClient(
        clientId: String,
        now: Instant,
    ): Flow<FollowUpEntity?> = flowOf(null)
    override suspend fun findBySyncStatus(status: SyncStatus): List<FollowUpEntity> =
        error("not stubbed")
    override suspend fun findPendingCompletions(status: SyncStatus): List<FollowUpEntity> =
        error("not stubbed")
    override suspend fun countPending(status: SyncStatus): Int = error("not stubbed")
    override fun observeCountPending(status: SyncStatus): Flow<Int> = error("not stubbed")
    override suspend fun markSyncStatus(ids: List<String>, status: SyncStatus) =
        error("not stubbed")
    override suspend fun markCompletionSyncStatus(ids: List<String>, status: SyncStatus) =
        error("not stubbed")
    override suspend fun markCompletedLocally(id: String, completedAt: Instant) =
        error("not stubbed")
    override suspend fun replaceAgenda(followUps: List<FollowUpEntity>) = error("not stubbed")
    override suspend fun deletePending() = error("not stubbed")
}

/** Noop API — tests only exercise local DAO paths. */
private class NoopFollowUpsApi : FollowUpsApi {
    override suspend fun getAgenda(
        from: String?,
        to: String?,
    ): ApiEnvelope<List<FollowUpResponse>> = error("not stubbed in this suite")
}
