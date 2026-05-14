package com.project.vortex.callsagent.data.remote.dto

import com.project.vortex.callsagent.data.device.DeviceInfo
import com.squareup.moshi.JsonClass

/**
 * Body for `POST /auth/login`. The backend rejects with `400` if
 * `device` is missing on AGENT logins (admin logins are not used
 * from this app).
 *
 * See `docs/SESSION_AND_VOIP_INTEGRATION.md § 1.1`.
 */
@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
    val device: DeviceInfoDto,
)

@JsonClass(generateAdapter = true)
data class DeviceInfoDto(
    val brand: String,
    val model: String,
    val osVersion: String,
    /** Wire value: "MOBILE" | "TABLET" | "OTHER". */
    val deviceType: String,
) {
    companion object {
        fun from(info: DeviceInfo): DeviceInfoDto = DeviceInfoDto(
            brand = info.brand,
            model = info.model,
            osVersion = info.osVersion,
            deviceType = info.deviceType.name,
        )
    }
}

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val accessToken: String,
)
