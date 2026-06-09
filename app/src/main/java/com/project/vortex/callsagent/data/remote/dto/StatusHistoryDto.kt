package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Paginated payload of `GET /clients/:id/status-history`. The backend wraps
 * this in the standard [ApiEnvelope]. Ordered newest-first by `createdAt`.
 */
@JsonClass(generateAdapter = true)
data class StatusHistoryResponse(
    val data: List<StatusHistoryEntryDto>,
    val total: Int,
    val page: Int,
    val limit: Int,
)

/**
 * One status transition. Only the 7 stable fields are modeled — the backend
 * still ships internal extras (ids, author email, metadata) that will be
 * removed in a later iteration; Moshi ignores them, so we stay drift-proof.
 */
@JsonClass(generateAdapter = true)
data class StatusHistoryEntryDto(
    /** Canonical row id — used as the timeline key. */
    val id: String,
    /** `ClientStatus` or null (null = initial creation). */
    val fromStatus: String?,
    /** `ClientStatus`. */
    val toStatus: String,
    /**
     * `RemovalReason` when [toStatus] is `REMOVED`. Pending backend support
     * in the status-history payload; null until then (the timeline just
     * omits the reason chip).
     */
    val removalReason: String?,
    /** `StatusChangeSource`. */
    val source: String?,
    val reason: String?,
    /** Author name snapshot — may be absent for system-driven changes. */
    val changedByName: String?,
    /** `ADMIN` | `AGENT` — may be absent for system-driven changes. */
    val changedByRole: String?,
    /** ISO-8601. */
    val createdAt: String,
)
