package com.project.vortex.callsagent.common.error

/**
 * Mirror of `calls-core/src/common/errors/error-codes.ts`. Every code
 * the backend can emit MUST be listed here so the [HttpErrorMapper]
 * can branch on it. Codes not listed here fall into
 * `ClientError.Unknown` / `AuthError.Unknown` and are reported to
 * telemetry as drift.
 *
 * **Sync policy:** when the backend ships a new code, add the
 * matching `const val` here in the same release. The pair is
 * coupled until there's codegen.
 */
object ErrorCodes {
    // ─── Auth ───────────────────────────────────────────────────────────
    const val AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS"
    const val AUTH_ACCOUNT_DISABLED = "AUTH_ACCOUNT_DISABLED"
    const val AUTH_DEVICE_REQUIRED = "AUTH_DEVICE_REQUIRED"
    const val AUTH_TOKEN_MISSING_SESSION = "AUTH_TOKEN_MISSING_SESSION"
    const val AUTH_SESSION_INVALIDATED = "AUTH_SESSION_INVALIDATED"

    // ─── Clients ────────────────────────────────────────────────────────
    const val CLIENT_REASON_REQUIRED = "CLIENT_REASON_REQUIRED"
    const val CLIENT_NOT_ASSIGNED = "CLIENT_NOT_ASSIGNED"
    const val CLIENT_TARGET_NOT_ALLOWED = "CLIENT_TARGET_NOT_ALLOWED"
    const val CLIENT_INTEREST_LEVEL_NOT_APPLICABLE = "CLIENT_INTEREST_LEVEL_NOT_APPLICABLE"
    const val CLIENT_DUPLICATE_KEY = "CLIENT_DUPLICATE_KEY"
    const val CLIENT_NOT_FOUND = "CLIENT_NOT_FOUND"

    // ─── Generic ────────────────────────────────────────────────────────
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"
    const val RESOURCE_CONFLICT = "RESOURCE_CONFLICT"
    const val FORBIDDEN = "FORBIDDEN"
    const val HTTP_ERROR = "HTTP_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"

    // ─── Mobile-side sentinels (NOT emitted by backend) ─────────────────
    /** Server returned a non-RFC9457 body — fall back to detail/title. */
    const val MALFORMED = "MALFORMED"

    /** Connection lost / DNS / SSL — IOException at the OkHttp layer. */
    const val NETWORK = "NETWORK"
}
