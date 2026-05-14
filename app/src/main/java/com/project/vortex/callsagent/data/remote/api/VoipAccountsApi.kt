package com.project.vortex.callsagent.data.remote.api

import com.project.vortex.callsagent.data.remote.dto.ApiEnvelope
import com.project.vortex.callsagent.data.remote.dto.VoipAccountDto
import retrofit2.http.GET

interface VoipAccountsApi {

    /**
     * Returns the VoIP account currently assigned to the authenticated
     * agent. `404 Not Found` with body `"No active VoIP account
     * assigned to this agent"` when none exists — the caller must map
     * that to the [VoipAvailability.Unassigned] state, not to a
     * generic error.
     */
    @GET("voip-accounts/me")
    suspend fun me(): ApiEnvelope<VoipAccountDto>
}
