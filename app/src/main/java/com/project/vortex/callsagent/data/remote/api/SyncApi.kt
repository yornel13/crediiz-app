package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.SyncRequest
import com.project.vortex.callsagent.data.remote.dto.SyncResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {

    @POST("sync/interactions")
    suspend fun sync(@Body request: SyncRequest): ApiEnvelope<SyncResponse>
}
