package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Generic response envelope used by the backend:
 * { "data": T, "statusCode": number }
 */
@JsonClass(generateAdapter = true)
data class ApiEnvelope<T>(
    val data: T,
    val statusCode: Int,
)
