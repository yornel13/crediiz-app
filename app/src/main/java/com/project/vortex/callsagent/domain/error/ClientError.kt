package com.project.vortex.callsagent.domain.error

/**
 * Typed failure modes for client-write operations
 * (`agent-status-change`, `update-interest-level`) and related
 * client-scoped reads. Lets the ViewModel emit a contextual snackbar
 * without parsing strings or HTTP codes in the UI layer.
 *
 * Mapping from `Throwable` happens in
 * [com.project.vortex.callsagent.data.error.HttpErrorMapper].
 *
 * Branch on the `code` field of [com.project.vortex.callsagent.data.remote.dto.ProblemDetails],
 * never on HTTP status or detail substrings.
 */
sealed interface ClientError {

    /**
     * `CLIENT_REASON_REQUIRED`. Mobile validates this locally so the
     * error should be rare — covers the race where two agents act on
     * the same client. Carries [toStatus] so the snackbar can be
     * specific ("Motivo obligatorio para CONVERTED").
     */
    data class ReasonRequired(val toStatus: String) : ClientError

    /** `CLIENT_NOT_ASSIGNED` — client is no longer assigned to this agent. */
    data object NotAssigned : ClientError

    /**
     * `CLIENT_TARGET_NOT_ALLOWED` — backend rejected the target
     * status for an OOB transition (e.g. trying to move to PENDING).
     * [toStatus] is the rejected destination.
     */
    data class TargetNotAllowed(val toStatus: String) : ClientError

    /**
     * `CLIENT_INTEREST_LEVEL_NOT_APPLICABLE` — tried to set the
     * thermometer on a client that isn't INTERESTED anymore.
     */
    data object InterestLevelNotApplicable : ClientError

    /** `CLIENT_NOT_FOUND` — server-side the client id no longer exists. */
    data object NotFound : ClientError

    /** HTTP 409 — concurrent modification (admin reassigned, duplicate, etc.). */
    data object Conflict : ClientError

    /**
     * HTTP 401 — session invalidated (NEW_LOGIN / ADMIN_REVOKE /
     * token expired). The auth interceptor already redirects to
     * login, but the snackbar gives the agent a hint.
     */
    data object SessionExpired : ClientError

    /** IOException — no connection or DNS / SSL hiccup. */
    data object Network : ClientError

    /**
     * Backend emitted a code that this mobile build doesn't
     * recognize, or the response wasn't RFC 9457. Carries both the
     * `code` and a human-readable `detail` so the snackbar shows
     * something useful and telemetry can flag the drift.
     */
    data class Unknown(val code: String, val detail: String) : ClientError
}
