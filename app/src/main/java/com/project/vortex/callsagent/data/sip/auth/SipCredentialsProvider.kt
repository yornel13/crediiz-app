package com.project.vortex.callsagent.data.sip.auth

import com.project.vortex.callsagent.data.sip.SipConfig

/**
 * Indirection between the SIP layer and credential storage. Lets us
 * swap Phase A (BuildConfig hardcoded) → Phase B (backend per-agent)
 * without touching call-flow code.
 */
interface SipCredentialsProvider {
    suspend fun current(): SipConfig
}
