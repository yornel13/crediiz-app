package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.common.telemetry.TelemetryLogger
import com.project.vortex.callsagent.data.error.ErrorMapper
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.LocalAgentStatusChangeDao
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity
import com.project.vortex.callsagent.data.remote.api.ClientsApi
import com.project.vortex.callsagent.data.remote.dto.AgentStatusChangeDto
import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import com.project.vortex.callsagent.data.remote.dto.UpdateInterestLevelDto
import com.project.vortex.callsagent.domain.error.ClientError
import com.project.vortex.callsagent.domain.result.OperationResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/**
 * Regression tests for the "silent rollback" class of bugs and the
 * RFC 9457 error-contract migration.
 *
 * Every test asserts BOTH invariants:
 *  1. The typed [ClientError] surfaces in [OperationResult.Failure]
 *     (no silent throws).
 *  2. Local state is restored to its previous value.
 *
 * Plus the new `Unknown` path forces a telemetry call so backend
 * drift surfaces in logcat before users complain.
 */
class ClientRepositoryAgentStatusChangeTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val telemetry = RecordingTelemetryLogger()
    private val mapper = ErrorMapper(moshi, telemetry)

    private val dao = FakeClientDao()
    private val statusChangeDao = FakeLocalAgentStatusChangeDao()
    private val api = FakeClientsApi()

    private val repo: ClientRepositoryImpl
        get() = ClientRepositoryImpl(api, dao, statusChangeDao, mapper)

    @Test
    fun `CLIENT_REASON_REQUIRED problem+json maps to ReasonRequired and rolls back`() = runBlocking {
        dao.statusForId = ClientStatus.PENDING
        dao.levelForId = null
        api.nextAgentStatusChangeError = buildHttpException(
            code = 400,
            body = """
                {
                  "code": "CLIENT_REASON_REQUIRED",
                  "title": "Reason required",
                  "status": 400,
                  "detail": "A reason is required when transitioning to CONVERTED without a call.",
                  "instance": "/api/clients/abc/agent-status-change",
                  "toStatus": "CONVERTED"
                }
            """.trimIndent(),
        )

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.CONVERTED,
            previousStatus = ClientStatus.PENDING,
            previousLevel = null,
            reason = null,
            level = null,
        )

        assertTrue(result is OperationResult.Failure)
        val err = (result as OperationResult.Failure).error
        assertTrue(err is ClientError.ReasonRequired)
        assertEquals("CONVERTED", (err as ClientError.ReasonRequired).toStatus)
        // Rollback
        assertEquals(ClientStatus.PENDING, dao.statusForId)
        assertNull(dao.levelForId)
        assertTrue(statusChangeDao.inserted.isEmpty())
    }

    @Test
    fun `IOException maps to Network and rolls back`() = runBlocking {
        dao.statusForId = ClientStatus.INTERESTED
        dao.levelForId = InterestLevel.WARM
        api.nextAgentStatusChangeError = IOException("offline")

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.REJECTED,
            previousStatus = ClientStatus.INTERESTED,
            previousLevel = InterestLevel.WARM,
            reason = "test",
            level = null,
        )

        assertTrue(result is OperationResult.Failure)
        assertEquals(
            ClientError.Network,
            (result as OperationResult.Failure).error,
        )
        assertEquals(ClientStatus.INTERESTED, dao.statusForId)
        assertEquals(InterestLevel.WARM, dao.levelForId)
        assertTrue(statusChangeDao.inserted.isEmpty())
    }

    @Test
    fun `unknown code falls back to Unknown and reports drift to telemetry`() = runBlocking {
        dao.statusForId = ClientStatus.PENDING
        api.nextAgentStatusChangeError = buildHttpException(
            code = 400,
            body = """
                {
                  "code": "BRAND_NEW_CODE_FROM_BACKEND",
                  "title": "New thing",
                  "status": 400,
                  "detail": "Server invented a code we don't know yet.",
                  "instance": "/api/clients/abc/agent-status-change"
                }
            """.trimIndent(),
        )

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.REJECTED,
            previousStatus = ClientStatus.PENDING,
            previousLevel = null,
            reason = "test",
            level = null,
        )

        assertTrue(result is OperationResult.Failure)
        val err = (result as OperationResult.Failure).error
        assertTrue(err is ClientError.Unknown)
        assertEquals("BRAND_NEW_CODE_FROM_BACKEND", (err as ClientError.Unknown).code)
        // Telemetry got a drift report.
        assertTrue(
            "TelemetryLogger should record the unknown code",
            telemetry.unknownCodes.any { it.first == "BRAND_NEW_CODE_FROM_BACKEND" },
        )
    }

    @Test
    fun `successful agentStatusChange persists local audit row`() = runBlocking {
        dao.statusForId = ClientStatus.PENDING
        dao.levelForId = null
        api.nextAgentStatusChangeError = null // success

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.DO_NOT_CALL,
            previousStatus = ClientStatus.PENDING,
            previousLevel = null,
            reason = "WhatsApp opt-out",
            level = null,
        )

        assertTrue(result is OperationResult.Success)
        assertEquals(ClientStatus.DO_NOT_CALL, dao.statusForId)
        assertEquals(1, statusChangeDao.inserted.size)
        val row = statusChangeDao.inserted.first()
        assertEquals(CLIENT_ID, row.clientId)
        assertEquals(ClientStatus.PENDING, row.fromStatus)
        assertEquals(ClientStatus.DO_NOT_CALL, row.toStatus)
        assertEquals("WhatsApp opt-out", row.reason)
    }

    companion object {
        private const val CLIENT_ID = "client-123"
    }

    // ─── Fakes ──────────────────────────────────────────────────────────

    /** Counts calls to telemetry hooks so tests can assert drift was reported. */
    private class RecordingTelemetryLogger : TelemetryLogger() {
        val unknownCodes = mutableListOf<Triple<String, String, String>>()
        val malformedBodies = mutableListOf<Triple<Int, String, String>>()

        override fun unknownErrorCode(code: String, detail: String, instance: String) {
            unknownCodes += Triple(code, detail, instance)
            // Skip super.call — android.util.Log isn't allowed in JVM tests
            // (would NPE without the testOptions stub).
        }

        override fun malformedErrorBody(httpCode: Int, instance: String, snippet: String) {
            malformedBodies += Triple(httpCode, instance, snippet)
        }
    }

    private class FakeClientsApi : ClientsApi {
        var nextAgentStatusChangeError: Throwable? = null

        override suspend fun getAssigned(status: String?): ApiEnvelope<List<ClientResponse>> =
            error("not used")

        override suspend fun updateInterestLevel(
            clientId: String,
            body: UpdateInterestLevelDto,
        ): ApiEnvelope<ClientResponse> = error("not used")

        override suspend fun agentStatusChange(
            clientId: String,
            body: AgentStatusChangeDto,
        ): ApiEnvelope<ClientResponse> {
            nextAgentStatusChangeError?.let { throw it }
            return ApiEnvelope(
                data = ClientResponse(
                    id = clientId,
                    name = "Stub",
                    phone = "0",
                    cedula = null,
                    ssNumber = null,
                    salary = null,
                    status = body.toStatus,
                    interestLevel = body.interestLevel,
                    assignedTo = null,
                    assignedAt = null,
                    callAttempts = 0,
                    wrongNumberCount = 0,
                    lastCalledAt = null,
                    lastOutcome = null,
                    lastNote = null,
                    queueOrder = 0,
                    extraData = null,
                    uploadBatchId = null,
                    createdAt = "2026-05-14T00:00:00Z",
                    updatedAt = "2026-05-14T00:00:00Z",
                ),
                statusCode = 200,
            )
        }
    }

    private class FakeClientDao : ClientDao {
        var statusForId: ClientStatus = ClientStatus.PENDING
        var levelForId: InterestLevel? = null

        override suspend fun setStatus(clientId: String, status: ClientStatus, now: Instant) {
            statusForId = status
        }

        override suspend fun setInterestLevel(
            clientId: String,
            level: InterestLevel?,
            now: Instant,
        ) {
            levelForId = level
        }

        override fun observeByStatus(status: ClientStatus): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observePendingNeverCalled(): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun searchPendingNeverCalled(query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observePendingForRetry(): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun searchPendingForRetry(query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override suspend fun findById(id: String): ClientEntity? = null
        override suspend fun findByNormalizedPhone(phone: String): ClientEntity? = null
        override fun searchByStatus(status: ClientStatus, query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observeRecent(since: Instant): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun searchRecent(since: Instant, query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observeUnscheduledInterested(now: Instant): Flow<List<ClientEntity>> = flowOf(emptyList())
        override suspend fun upsert(clients: List<ClientEntity>) {}
        override suspend fun replaceAllByStatus(status: ClientStatus, clients: List<ClientEntity>) {}
        override suspend fun deleteByStatus(status: ClientStatus) {}
        override suspend fun replaceAll(clients: List<ClientEntity>) {}
        override suspend fun deleteAll() {}
        override suspend fun applyInteractionUpdate(
            clientId: String,
            lastCalledAt: Instant,
            lastOutcome: CallOutcome,
            newStatus: ClientStatus,
            now: Instant,
        ) {}
        // Added when ClientDao.refineOutcomeAndStatus shipped — this fake
        // is for the agent-status-change tests, which don't exercise the
        // PostCall refine path, so a noop matches the rest of the stubs.
        override suspend fun refineOutcomeAndStatus(
            clientId: String,
            outcome: CallOutcome,
            newStatus: ClientStatus,
            now: Instant,
        ) {}
        override suspend fun updateLastNote(clientId: String, note: String, now: Instant) {}
    }

    private class FakeLocalAgentStatusChangeDao : LocalAgentStatusChangeDao {
        val inserted = mutableListOf<LocalAgentStatusChangeEntity>()

        override suspend fun insert(entity: LocalAgentStatusChangeEntity) {
            inserted += entity
        }

        override suspend fun deleteById(id: String) {
            inserted.removeAll { it.id == id }
        }

        override fun observeRecent(since: Instant): Flow<List<LocalAgentStatusChangeEntity>> =
            flowOf(emptyList())

        override suspend fun deleteAll() {
            inserted.clear()
        }
    }

    private fun buildHttpException(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/problem+json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }
}
