package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.util.JwtDecoder
import com.project.vortex.callsagent.data.device.DeviceInfo
import com.project.vortex.callsagent.data.error.ErrorMapper
import com.project.vortex.callsagent.data.local.db.AppDatabase
import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import com.project.vortex.callsagent.data.local.preferences.DeviceOwnerPreferences
import com.project.vortex.callsagent.data.remote.api.AuthApi
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.data.remote.dto.DeviceInfoDto
import com.project.vortex.callsagent.data.remote.dto.LoginRequest
import com.project.vortex.callsagent.data.voip.VoipAccountRepository
import com.project.vortex.callsagent.data.voip.VoipRefreshOrchestrator
import com.project.vortex.callsagent.domain.error.AuthError
import com.project.vortex.callsagent.domain.repository.AuthRepository
import com.project.vortex.callsagent.domain.result.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferences,
    private val deviceOwnerPreferences: DeviceOwnerPreferences,
    private val database: AppDatabase,
    private val voipAccountRepository: VoipAccountRepository,
    private val voipRefreshOrchestrator: VoipRefreshOrchestrator,
    private val syncScheduler: SyncScheduler,
    private val errorMapper: ErrorMapper,
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String,
        device: DeviceInfo,
    ): OperationResult<Unit, AuthError> = withContext(Dispatchers.IO) {
        try {
            val envelope = authApi.login(
                LoginRequest(
                    email = email.trim(),
                    password = password,
                    device = DeviceInfoDto.from(device),
                ),
            )
            val token = envelope.data.accessToken

            val payload = JwtDecoder.decode(token)
                ?: return@withContext OperationResult.Failure(
                    AuthError.Unknown(
                        code = "JWT_DECODE",
                        detail = "Invalid JWT received from server",
                    ),
                )

            // Identity-keyed wipe. Local data belongs to ONE agent (the
            // "data owner"); it survives logout so un-synced PENDING rows
            // and the activity cache outlive session drops (JWT expiry).
            // It is destroyed only here, when a DIFFERENT agent takes the
            // device. Order is load-bearing: cancel sync workers and wipe
            // BEFORE saveSession, so no worker can push the previous
            // agent's rows under the new agent's JWT (the server infers
            // authorship from the token). A null owner (fresh install or
            // pre-ownership upgrade) also wipes — we cannot prove the data
            // belongs to this agent.
            val newAgentId = payload.subject
            if (deviceOwnerPreferences.currentOwner() != newAgentId) {
                syncScheduler.cancelAll()
                database.clearAllTables()
                deviceOwnerPreferences.setOwner(newAgentId)
            }

            authPreferences.saveSession(
                token = token,
                agentId = newAgentId,
                email = payload.email,
                name = payload.email.substringBefore('@'), // placeholder until /agents/me exists
            )
            OperationResult.Success(Unit)
        } catch (err: Throwable) {
            OperationResult.Failure(errorMapper.toAuthError(err))
        }
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        // Centralized SESSION teardown — used by both the manual logout and
        // the automatic one (SessionEventBus on JWT expiry). Order:
        //   1. Cancel sync workers so the 20-min periodic doesn't keep
        //      firing without a session (in-flight workers may still finish;
        //      SyncManager's identity guard blocks their push).
        //   2. Stop the orchestrator so its periodic job doesn't race a
        //      fresh REGISTER attempt against an empty cache. stop() also
        //      tears down the Linphone Core.
        //   3. Clear the VoIP cache so a re-login starts blank.
        //   4. Clear the JWT.
        //
        // Room is deliberately NOT wiped here. Local data (including
        // un-synced PENDING rows) belongs to the device's data owner and
        // must survive session drops — a JWT expiry used to destroy notes
        // that had never reached the server. The wipe happens on LOGIN,
        // keyed by identity change (see login() above).
        syncScheduler.cancelAll()
        voipRefreshOrchestrator.stop()
        voipAccountRepository.clear()
        authPreferences.clear()
    }

    override fun isLoggedIn(): Flow<Boolean> =
        authPreferences.accessTokenFlow.map { token ->
            !token.isNullOrBlank() && !JwtDecoder.isExpired(token)
        }

    override suspend fun currentAgentId(): String? = authPreferences.agentIdFlow.first()

    override fun agentNameFlow(): Flow<String?> = authPreferences.agentNameFlow

    override fun agentEmailFlow(): Flow<String?> = authPreferences.agentEmailFlow
}
