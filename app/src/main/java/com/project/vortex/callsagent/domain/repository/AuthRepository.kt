package com.project.vortex.callsagent.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /**
     * Authenticate with the backend. On success the JWT is persisted
     * and the agent identity is decoded from the token.
     */
    suspend fun login(email: String, password: String): Result<Unit>

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
