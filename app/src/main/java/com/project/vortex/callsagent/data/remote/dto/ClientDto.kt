package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Server representation of a Client (agent assigned).
 * Note: `extraData` is a free-form map of any extra columns from the uploaded Excel.
 */
@JsonClass(generateAdapter = true)
data class ClientResponse(
    @Json(name = "_id") val id: String,
    val name: String,
    val phone: String,
    val status: String,
    val assignedTo: String?,
    val assignedAt: String?,
    val callAttempts: Int,
    val lastCalledAt: String?,
    val lastOutcome: String?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>?,
    val uploadBatchId: String?,
    val createdAt: String,
    val updatedAt: String,
)
