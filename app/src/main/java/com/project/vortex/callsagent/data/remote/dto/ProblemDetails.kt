package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * RFC 9457 (Problem Details for HTTP APIs) payload as emitted by the
 * `calls-core` global exception filter. Every 4xx/5xx response carries
 * `Content-Type: application/problem+json` with this shape.
 *
 * **Contract:** the [code] field is the **only** stable discriminator
 * — branching MUST happen on `code`, never on [status] or [detail].
 * The latter two are documentation for humans; [code] is the contract
 * for machines.
 *
 * Extras ([toStatus], [currentStatus], [field], [value], [clientId],
 * [errors]) are populated by the backend per error type; the mobile
 * mapper picks whichever fields the matched code needs and ignores
 * the rest.
 *
 * See `calls-core/docs/ERROR_CONTRACT.md` for the canonical reference.
 */
@JsonClass(generateAdapter = true)
data class ProblemDetails(
    val code: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val type: String? = null,
    val traceId: String? = null,
    val errors: List<String>? = null,
    // ─── Extras populated only by specific codes ───
    val toStatus: String? = null,
    val currentStatus: String? = null,
    val field: String? = null,
    val value: String? = null,
    val clientId: String? = null,
)
