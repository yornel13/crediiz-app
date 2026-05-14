package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.util.JwtDecoder
import com.project.vortex.callsagent.data.device.DeviceInfo
import com.project.vortex.callsagent.data.local.db.AppDatabase
import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import com.project.vortex.callsagent.data.remote.api.AuthApi
import com.project.vortex.callsagent.data.remote.dto.DeviceInfoDto
import com.project.vortex.callsagent.data.remote.dto.LoginRequest
import com.project.vortex.callsagent.data.voip.VoipAccountRepository
import com.project.vortex.callsagent.data.voip.VoipRefreshOrchestrator
import com.project.vortex.callsagent.domain.repository.AuthRepository
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
    private val database: AppDatabase,
    private val voipAccountRepository: VoipAccountRepository,
    private val voipRefreshOrchestrator: VoipRefreshOrchestrator,
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String,
        device: DeviceInfo,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val envelope = authApi.login(
                    LoginRequest(
                        email = email.trim(),
                        password = password,
                        device = DeviceInfoDto.from(device),
                    ),
                )
                val token = envelope.data.accessToken

                val payload = JwtDecoder.decode(token)
                    ?: error("Invalid JWT received from server")

                authPreferences.saveSession(
                    token = token,
                    agentId = payload.subject,
                    email = payload.email,
                    name = payload.email.substringBefore('@'), // placeholder until /agents/me exists
                )
            }
        }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        // Centralized cleanup. Order:
        //   1. Stop the orchestrator first so its periodic job doesn't
        //      race a fresh REGISTER attempt against an empty cache.
        //      stop() also tears down the Linphone Core (no engine
        //      activity without credentials).
        //   2. Clear the VoIP cache so a re-login starts blank.
        //   3. Clear the JWT.
        //   4. Wipe Room — the next session starts clean.
        voipRefreshOrchestrator.stop()
        voipAccountRepository.clear()
        authPreferences.clear()
        database.clearAllTables()
    }

    override fun isLoggedIn(): Flow<Boolean> =
        authPreferences.accessTokenFlow.map { token ->
            !token.isNullOrBlank() && !JwtDecoder.isExpired(token)
        }

    override suspend fun currentAgentId(): String? = authPreferences.agentIdFlow.first()

    override fun agentNameFlow(): Flow<String?> = authPreferences.agentNameFlow

    override fun agentEmailFlow(): Flow<String?> = authPreferences.agentEmailFlow
}
