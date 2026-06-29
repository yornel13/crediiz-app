package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.common.telemetry.TelemetryLogger
import com.project.vortex.callsagent.data.error.ErrorMapper
import com.project.vortex.callsagent.data.local.db.ClientAttemptCount
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.LocalAgentStatusChangeDao
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity
import com.project.vortex.callsagent.data.remote.api.ClientsApi
import com.project.vortex.callsagent.data.remote.dto.AgentStatusChangeDto
import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import com.project.vortex.callsagent.data.remote.dto.StatusHistoryResponse
import com.project.vortex.callsagent.data.remote.dto.UpsertQuotationDto
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
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/**
 * Regression tests for [ClientRepositoryImpl.agentStatusChange] — the
 * typed error contract (RFC 9457) and the local-audit side effect.
 *
 * The repository does NOT write optimistically: it calls the API first and
 * only mirrors the server-returned client on success. So each test asserts:
 *  1. A failure surfaces as a typed [ClientError] in [OperationResult.Failure]
 *     (no silent throws) and leaves the local store untouched.
 *  2. A success persists the server status plus a local audit row.
 *
 * The `Unknown` path also forces a telemetry call so backend drift shows up
 * in logcat before users complain.
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
    fun `CLIENT_REASON_REQUIRED problem+json maps to ReasonRequired and writes nothing`() = runBlocking {
        dao.existing = clientEntity(ClientStatus.PENDING)
        api.nextAgentStatusChangeError = buildHttpException(
            code = 400,
            body = """
                {
                  "code": "CLIENT_REASON_REQUIRED",
                  "title": "Reason required",
                  "status": 400,
                  "detail": "A reason is required when transitioning to REMOVED without a call.",
                  "instance": "/api/clients/abc/agent-status-change",
                  "toStatus": "REMOVED"
                }
            """.trimIndent(),
        )

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.REMOVED,
            removalReason = null,
            reason = null,
        )

        assertTrue(result is OperationResult.Failure)
        val err = (result as OperationResult.Failure).error
        assertTrue(err is ClientError.ReasonRequired)
        assertEquals("REMOVED", (err as ClientError.ReasonRequired).toStatus)
        // No local write on failure.
        assertTrue(dao.upserted.isEmpty())
        assertTrue(statusChangeDao.inserted.isEmpty())
    }

    @Test
    fun `IOException maps to Network and writes nothing`() = runBlocking {
        dao.existing = clientEntity(ClientStatus.INTERESTED)
        api.nextAgentStatusChangeError = IOException("offline")

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.REMOVED,
            removalReason = RemovalReason.WRONG_NUMBER,
            reason = "test",
        )

        assertTrue(result is OperationResult.Failure)
        assertEquals(ClientError.Network, (result as OperationResult.Failure).error)
        // No local write on failure.
        assertTrue(dao.upserted.isEmpty())
        assertTrue(statusChangeDao.inserted.isEmpty())
    }

    @Test
    fun `unknown code falls back to Unknown and reports drift to telemetry`() = runBlocking {
        dao.existing = clientEntity(ClientStatus.PENDING)
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
            toStatus = ClientStatus.REMOVED,
            removalReason = RemovalReason.OTHER,
            reason = "test",
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
    fun `successful agentStatusChange persists server status and a local audit row`() = runBlocking {
        dao.existing = clientEntity(ClientStatus.PENDING)
        api.nextAgentStatusChangeError = null // success

        val result = repo.agentStatusChange(
            clientId = CLIENT_ID,
            toStatus = ClientStatus.REMOVED,
            removalReason = RemovalReason.DO_NOT_CALL,
            reason = "WhatsApp opt-out",
        )

        assertTrue(result is OperationResult.Success)
        assertEquals(ClientStatus.REMOVED, (result as OperationResult.Success).value)
        // Server-returned client mirrored locally.
        assertEquals(1, dao.upserted.size)
        assertEquals(ClientStatus.REMOVED, dao.upserted.first().status)
        // Audit row reflects the transition.
        assertEquals(1, statusChangeDao.inserted.size)
        val row = statusChangeDao.inserted.first()
        assertEquals(CLIENT_ID, row.clientId)
        assertEquals(ClientStatus.PENDING, row.fromStatus)
        assertEquals(ClientStatus.REMOVED, row.toStatus)
        assertEquals("WhatsApp opt-out", row.reason)
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private fun clientEntity(status: ClientStatus): ClientEntity = ClientEntity(
        id = CLIENT_ID,
        name = "Stub",
        phone = "0",
        cedula = null,
        ssNumber = null,
        salary = null,
        status = status,
        removalReason = null,
        assignedTo = null,
        assignedAt = null,
        callAttempts = 0,
        agentCallAttempts = 0,
        lastCalledAt = null,
        lastOutcome = null,
        lastNote = null,
        queueOrder = 0,
        extraData = null,
        quotationValidation = null,
        quotationBank = null,
        quotationQuotedAmount = null,
        quotationBiweeklyPayment = null,
        quotationNotes = null,
        quotationUpdatedBy = null,
        quotationUpdatedAt = null,
        uploadBatchId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun clientResponse(id: String, status: String, removalReason: String?): ClientResponse =
        ClientResponse(
            id = id,
            name = "Stub",
            phone = "0",
            cedula = null,
            ssNumber = null,
            salary = null,
            status = status,
            removalReason = removalReason,
            assignedTo = null,
            assignedAt = null,
            callAttempts = 0,
            agentCallAttempts = 0,
            lastCalledAt = null,
            lastOutcome = null,
            lastNote = null,
            queueOrder = 0,
            extraData = null,
            quotation = null,
            uploadBatchId = null,
            createdAt = "2026-05-14T00:00:00Z",
            updatedAt = "2026-05-14T00:00:00Z",
        )

    companion object {
        private const val CLIENT_ID = "client-123"
    }

    // ─── Fakes ──────────────────────────────────────────────────────────────

    /** Counts calls to telemetry hooks so tests can assert drift was reported. */
    private class RecordingTelemetryLogger : TelemetryLogger() {
        val unknownCodes = mutableListOf<Triple<String, String, String>>()
        val malformedBodies = mutableListOf<Triple<Int, String, String>>()

        override fun unknownErrorCode(code: String, detail: String, instance: String) {
            unknownCodes += Triple(code, detail, instance)
            // Skip super.call — android.util.Log isn't allowed in JVM tests.
        }

        override fun malformedErrorBody(httpCode: Int, instance: String, snippet: String) {
            malformedBodies += Triple(httpCode, instance, snippet)
        }
    }

    private inner class FakeClientsApi : ClientsApi {
        var nextAgentStatusChangeError: Throwable? = null

        override suspend fun getAssigned(status: String?): ApiEnvelope<List<ClientResponse>> =
            error("not used")

        override suspend fun getStatusHistory(
            clientId: String,
            page: Int,
            limit: Int,
        ): ApiEnvelope<StatusHistoryResponse> = error("not used")

        override suspend fun upsertQuotation(
            clientId: String,
            body: UpsertQuotationDto,
        ): ApiEnvelope<ClientResponse> = error("not used")

        override suspend fun agentStatusChange(
            clientId: String,
            body: AgentStatusChangeDto,
        ): ApiEnvelope<ClientResponse> {
            nextAgentStatusChangeError?.let { throw it }
            return ApiEnvelope(
                data = clientResponse(
                    id = clientId,
                    status = body.toStatus,
                    removalReason = body.removalReason,
                ),
                statusCode = 200,
            )
        }
    }

    private class FakeClientDao : ClientDao {
        /** Row returned by [findById] — drives `fromStatus` in the repo. */
        var existing: ClientEntity? = null

        /** Captures every upsert so tests can assert the local mirror write. */
        val upserted = mutableListOf<ClientEntity>()

        override suspend fun findById(id: String): ClientEntity? = existing
        override suspend fun upsert(clients: List<ClientEntity>) { upserted += clients }

        override suspend fun setStatus(clientId: String, status: ClientStatus, now: Instant) {}
        override suspend fun setRemovalReason(clientId: String, reason: RemovalReason?, now: Instant) {}
        override suspend fun applyInteractionUpdate(
            clientId: String,
            lastCalledAt: Instant,
            lastOutcome: CallOutcome,
            now: Instant,
        ) {}
        override suspend fun refineOutcome(clientId: String, outcome: CallOutcome, now: Instant) {}
        override suspend fun updateLastNote(clientId: String, note: String, now: Instant) {}
        override suspend fun agentCallAttemptsByIds(ids: List<String>): List<ClientAttemptCount> =
            emptyList()
        override suspend fun deleteNotIn(ids: List<String>) {}
        override suspend fun deleteAll() {}
        override suspend fun findByNormalizedPhone(phone: String): ClientEntity? = null

        override fun observeByStatus(status: ClientStatus): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observePendingNeverCalled(): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun searchPendingNeverCalled(query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observePendingForRetry(): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun searchPendingForRetry(query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observeById(id: String): Flow<ClientEntity?> = flowOf(null)
        override fun searchByStatus(status: ClientStatus, query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observeRecent(since: Instant): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun searchRecent(since: Instant, query: String): Flow<List<ClientEntity>> = flowOf(emptyList())
        override fun observeUnscheduledActive(): Flow<List<ClientEntity>> = flowOf(emptyList())
    }

    private class FakeLocalAgentStatusChangeDao : LocalAgentStatusChangeDao {
        val inserted = mutableListOf<LocalAgentStatusChangeEntity>()

        override suspend fun insert(entity: LocalAgentStatusChangeEntity) { inserted += entity }
        override suspend fun deleteById(id: String) { inserted.removeAll { it.id == id } }
        override fun observeRecent(since: Instant): Flow<List<LocalAgentStatusChangeEntity>> =
            flowOf(emptyList())
        override suspend fun deleteAll() { inserted.clear() }
    }

    private fun buildHttpException(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/problem+json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }
}
