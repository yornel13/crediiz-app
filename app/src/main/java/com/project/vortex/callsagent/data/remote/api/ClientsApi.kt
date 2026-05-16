package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.AgentStatusChangeDto
import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import com.project.vortex.callsagent.data.remote.dto.UpdateInterestLevelDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ClientsApi {

    /**
     * Get clients assigned to the authenticated agent.
     * @param status "PENDING" (default), "IN_PROGRESS" or "INTERESTED".
     */
    @GET("clients/assigned")
    suspend fun getAssigned(
        @Query("status") status: String? = null,
    ): ApiEnvelope<List<ClientResponse>>

    /**
     * Promote/demote the thermometer of an INTERESTED client. Server
     * returns the updated client; we use the response to confirm the
     * write but the call-site already wrote optimistically to Room.
     *
     * @throws retrofit2.HttpException 400 if the client is not INTERESTED.
     */
    @PATCH("clients/{id}/interest-level")
    suspend fun updateInterestLevel(
        @Path("id") clientId: String,
        @Body body: UpdateInterestLevelDto,
    ): ApiEnvelope<ClientResponse>

    /**
     * Move an assigned client to a new status without making a call.
     * Used for out-of-band signals (client wrote on WhatsApp, etc).
     * Server rejects with 400 on disallowed transitions, 403 if the
     * client isn't assigned to the requester.
     */
    @POST("clients/{id}/agent-status-change")
    suspend fun agentStatusChange(
        @Path("id") clientId: String,
        @Body body: AgentStatusChangeDto,
    ): ApiEnvelope<ClientResponse>
}
