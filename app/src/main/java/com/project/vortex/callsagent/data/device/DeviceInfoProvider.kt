package com.project.vortex.callsagent.data.device

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the device fingerprint sent in the login body. The backend
 * uses this to identify the agent's session in `Session.device` and
 * to display human-readable info in the admin panel ("Galaxy Tab A8 ·
 * Android 14").
 *
 * Heuristic for `deviceType`: `smallestScreenWidthDp >= 600` is the
 * standard Android cutoff between phone and tablet form-factors.
 */
@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): DeviceInfo = DeviceInfo(
        brand = Build.MANUFACTURER ?: UNKNOWN,
        model = Build.MODEL ?: UNKNOWN,
        osVersion = "Android ${Build.VERSION.RELEASE ?: UNKNOWN}",
        deviceType = if (context.resources.configuration.smallestScreenWidthDp >= TABLET_DP) {
            DeviceType.TABLET
        } else {
            DeviceType.MOBILE
        },
    )

    companion object {
        private const val UNKNOWN = "Unknown"
        private const val TABLET_DP = 600
    }
}

/**
 * In-memory representation. Crosses the layer boundary into the auth
 * flow; the wire-level shape lives in [com.project.vortex.callsagent
 * .data.remote.dto.DeviceInfoDto].
 */
data class DeviceInfo(
    val brand: String,
    val model: String,
    val osVersion: String,
    val deviceType: DeviceType,
)

enum class DeviceType {
    MOBILE,
    TABLET,
    OTHER,
}
