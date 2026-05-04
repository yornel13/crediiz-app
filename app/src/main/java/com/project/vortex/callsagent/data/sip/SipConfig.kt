package com.project.vortex.callsagent.data.sip

/**
 * Immutable SIP configuration. Phase A reads these from BuildConfig
 * (sourced from local.properties at build time). Phase B replaces the
 * provider with a backend-issued, per-agent fetch — without touching
 * any consumer of [SipConfig].
 */
data class SipConfig(
    val server: String,
    val user: String,
    val password: String,
) {
    /** Linphone identity URI. */
    val identity: String get() = "sip:$user@$server"
}
