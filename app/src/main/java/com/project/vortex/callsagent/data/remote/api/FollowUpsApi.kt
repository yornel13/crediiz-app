package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.FollowUpResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FollowUpsApi {

    /**
     * Agent's personal agenda (pending follow-ups ordered by scheduledAt).
     * Optional date range filter (ISO-8601 strings).
     */
    @GET("follow-ups/agenda")
    suspend fun getAgenda(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ApiEnvelope<List<FollowUpResponse>>
}
