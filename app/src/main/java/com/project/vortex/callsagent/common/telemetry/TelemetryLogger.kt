package com.project.vortex.callsagent.common.telemetry

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight drift-detection sink. Errors classified as
 * `Unknown`/`MALFORMED` (i.e. backend started emitting a code the
 * mobile mirror doesn't list yet) go through here so the dev team
 * notices the gap before users do.
 *
 * Today: logs to logcat with the dedicated `TelemetryLogger` tag so
 * `adb logcat -s TelemetryLogger:W` is enough to surface them.
 *
 * TODO(CRASHLYTICS): when Crashlytics ships, mirror these calls to
 * `FirebaseCrashlytics.getInstance().log(...)` — same call-sites,
 * no API change in this class.
 */
@Singleton
open class TelemetryLogger @Inject constructor() {

    /** Drift detected — backend emits a code the mobile mirror doesn't recognize. */
    open fun unknownErrorCode(code: String, detail: String, instance: String) {
        Log.w(
            TAG,
            "Unknown backend error code='$code' at $instance (detail: $detail) — " +
                "add it to ErrorCodes.kt and the matching sealed",
        )
    }

    /** Backend responded with a non-RFC9457 body (HTML 502, empty, etc.). */
    open fun malformedErrorBody(httpCode: Int, instance: String, snippet: String) {
        Log.w(
            TAG,
            "Malformed error body for HTTP $httpCode at $instance — " +
                "first 200 chars: ${snippet.take(200)}",
        )
    }

    companion object {
        private const val TAG = "TelemetryLogger"
    }
}
