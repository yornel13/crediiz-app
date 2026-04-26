package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.domain.model.Client
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ClientRepository {

    /** Fetch assigned clients from the server and replace the local cache. */
    suspend fun refreshAssigned(status: ClientStatus = ClientStatus.PENDING): Result<Unit>

    /** Observe assigned clients by status from the local DB. */
    fun observeAssigned(status: ClientStatus): Flow<List<Client>>

    /** Local search (name/phone substring). */
    fun searchAssigned(status: ClientStatus, query: String): Flow<List<Client>>

    /**
     * Observe clients called inside the given time window, regardless of
     * status/outcome. Drives the "Recientes" view (24 h window for v1.0).
     */
    fun observeRecent(since: Instant): Flow<List<Client>>

    /** Local search over the "Recientes" feed. */
    fun searchRecent(since: Instant, query: String): Flow<List<Client>>

    /** Find a single client by id. */
    suspend fun findById(id: String): Client?

    /**
     * Match by the last 8 digits of the phone number (handles country-code
     * prefix variations). Returns null if no assigned client matches —
     * used for incoming-call caller identification.
     */
    suspend fun findByPhone(phone: String): Client?

    /**
     * Optimistically apply an interaction's side-effects on the local client row,
     * mirroring what the backend will do on sync.
     */
    suspend fun applyInteractionLocally(
        clientId: String,
        outcome: CallOutcome,
        callStartedAt: Instant,
    )

    /** Update the denormalized lastNote field after saving a note locally. */
    suspend fun updateLastNoteLocally(clientId: String, note: String)
}
