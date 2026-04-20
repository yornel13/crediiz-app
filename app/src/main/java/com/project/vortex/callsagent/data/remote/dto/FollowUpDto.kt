package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Follow-up entry from the agenda endpoint. When fetched via the populated query,
 * `clientId` may be a nested object. The Agent app needs both id and client info,
 * so the server populates name/phone/extraData/callAttempts/lastOutcome/lastNote.
 */
@JsonClass(generateAdapter = true)
data class FollowUpResponse(
    @Json(name = "_id") val id: String,
    val mobileSyncId: String,
    val clientId: PopulatedClient,
    val agentId: String,
    val interactionId: String?,
    val scheduledAt: String,
    val reason: String,
    val status: String,
    val completedAt: String?,
    val cancelledAt: String?,
    val cancelReason: String?,
    val deviceCreatedAt: String,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * The `clientId` field comes populated with these selected fields from Client.
 */
@JsonClass(generateAdapter = true)
data class PopulatedClient(
    @Json(name = "_id") val id: String,
    val name: String,
    val phone: String,
    val extraData: Map<String, Any?>?,
    val callAttempts: Int?,
    val lastOutcome: String?,
    val lastNote: String?,
)
