package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.AgentStatusChangeDto
import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import com.project.vortex.callsagent.data.remote.dto.StatusHistoryResponse
import com.project.vortex.callsagent.data.remote.dto.UpsertQuotationDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ClientsApi {

    /**
     * Get clients assigned to the authenticated agent. The backend filters
     * by the requesting agent's id (N:M assignment).
     * @param status one [com.project.vortex.callsagent.common.enums.ClientStatus]
     *   ("PENDING" default, "INTERESTED" or "CITED" — the active states).
     */
    @GET("clients/assigned")
    suspend fun getAssigned(
        @Query("status") status: String? = null,
    ): ApiEnvelope<List<ClientResponse>>

    /**
     * Move an assigned client to a new status without making a call.
     * Used for out-of-band signals (client wrote on WhatsApp, etc).
     *
     * Returns the resulting client — the caller **must reconcile against
     * the returned `status`**: a blocked transition responds 200 with the
     * client unchanged (high-water-mark / quorum rules). 403 if the client
     * isn't assigned to the requester; 400 if `removalReason` is missing
     * on a move to REMOVED.
     */
    @POST("clients/{id}/agent-status-change")
    suspend fun agentStatusChange(
        @Path("id") clientId: String,
        @Body body: AgentStatusChangeDto,
    ): ApiEnvelope<ClientResponse>

    /**
     * Canonical status history of a client (any actor: this agent, other
     * agents, admin, system). Newest-first. `AGENT` may only read clients
     * assigned to them (else 403); 404 if the client doesn't exist.
     */
    @GET("clients/{id}/status-history")
    suspend fun getStatusHistory(
        @Path("id") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): ApiEnvelope<StatusHistoryResponse>

    /**
     * Upsert the client's quotation (idempotent full-object replace).
     * Returns the full client with the quotation embedded. `AGENT` may only
     * write clients assigned to them (403 otherwise); 404 unknown; 400 invalid.
     */
    @PUT("clients/{id}/quotation")
    suspend fun upsertQuotation(
        @Path("id") clientId: String,
        @Body body: UpsertQuotationDto,
    ): ApiEnvelope<ClientResponse>
}
