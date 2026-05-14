package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Wire shape of `GET /voip-accounts/me`. Mirrors the schema in
 * `calls-core/src/voip-accounts/schemas/voip-account.schema.ts`
 * exactly — fields the agent cares about plus a few we pass through
 * unchanged for diagnostics.
 *
 * `agentId` is populated to a string id for the agent; we ignore it
 * here because the SIP credentials are what we need.
 *
 * See `docs/SESSION_AND_VOIP_INTEGRATION.md § 2.2`.
 */
@JsonClass(generateAdapter = true)
data class VoipAccountDto(
    val _id: String,
    val label: String?,
    val did: String,
    val provider: String,
    val sipUsername: String,
    val sipPassword: String,
    val sipDomain: String,
    val agentId: String?,
    val isActive: Boolean,
)
