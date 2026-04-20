package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ClientsApi {

    /**
     * Get clients assigned to the authenticated agent.
     * @param status "PENDING" (default) or "INTERESTED".
     */
    @GET("clients/assigned")
    suspend fun getAssigned(
        @Query("status") status: String? = null,
    ): ApiEnvelope<List<ClientResponse>>
}
