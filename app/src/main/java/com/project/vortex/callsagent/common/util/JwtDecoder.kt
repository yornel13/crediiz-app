package com.project.vortex.callsagent.common.util

import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * JWT payload decoder (base64url of the second segment).
 *
 * Note: we DO NOT verify the signature here — that's the backend's job.
 * This is only for reading local claims (agentId, email, role) after a
 * successful login response.
 */
object JwtDecoder {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(JwtPayload::class.java)

    @JsonClass(generateAdapter = false)
    data class JwtPayload(
        @Json(name = "sub") val subject: String,
        val email: String,
        val role: String,
        val iat: Long? = null,
        val exp: Long? = null,
    )

    fun decode(token: String): JwtPayload? {
        val parts = token.split(".")
        if (parts.size != 3) return null

        return try {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = String(payloadBytes, Charsets.UTF_8)
            adapter.fromJson(json)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Returns true when the token's `exp` claim is in the future (or missing).
     */
    fun isExpired(token: String): Boolean {
        val payload = decode(token) ?: return true
        val exp = payload.exp ?: return false
        return exp * 1000L < System.currentTimeMillis()
    }
}
