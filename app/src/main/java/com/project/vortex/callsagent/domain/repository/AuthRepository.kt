package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.data.device.DeviceInfo
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /**
     * Authenticate with the backend. On success the JWT is persisted
     * and the agent identity is decoded from the token.
     *
     * The backend requires `device` for AGENT logins to track the
     * single-active-session per agent. See
     * `docs/SESSION_AND_VOIP_INTEGRATION.md § 1`.
     */
    suspend fun login(email: String, password: String, device: DeviceInfo): Result<Unit>

    /** Clear the saved token and all local agent-scoped data. */
    suspend fun logout()

    /** Emits true whenever there is a valid (non-expired) token stored. */
    fun isLoggedIn(): Flow<Boolean>

    /** Current agent id, decoded from the JWT. */
    suspend fun currentAgentId(): String?

    /** Current agent name (cached at login time). */
    fun agentNameFlow(): Flow<String?>

    /** Current agent email (cached at login time). */
    fun agentEmailFlow(): Flow<String?>
}
