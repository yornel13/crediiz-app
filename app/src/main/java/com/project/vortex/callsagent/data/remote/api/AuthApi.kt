package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.LoginRequest
import com.project.vortex.callsagent.data.remote.dto.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<LoginResponse>
}
