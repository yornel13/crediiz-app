package com.project.vortex.callsagent.domain.error

/**
 * Typed failure modes for the authentication flow (login + token
 * lifecycle). Lets the LoginScreen decide between:
 *  - inline error under the form ([InvalidCredentials] — the user
 *    will fix it right there),
 *  - blocking snackbar ([AccountDisabled], [SessionExpired]).
 *
 * Mapping from `Throwable` lives in
 * [com.project.vortex.callsagent.data.error.HttpErrorMapper].
 */
sealed interface AuthError {

    /** Wrong email/password — the most common case. Inline under the form. */
    data object InvalidCredentials : AuthError

    /** Admin disabled the account (`isActive === false`). */
    data object AccountDisabled : AuthError

    /**
     * Client sent `/auth/login` without the `device` payload — this
     * is a **mobile bug**, never a user-facing scenario. The
     * snackbar copy nudges to update the app.
     */
    data object DeviceRequired : AuthError

    /**
     * Session was invalidated by the backend (new login on another
     * device / admin revoke / token TTL expired). Re-login required.
     */
    data object SessionExpired : AuthError

    /** IOException — no connection / DNS / SSL. */
    data object Network : AuthError

    /**
     * Backend emitted a code that the mobile mirror doesn't list yet,
     * or the response wasn't RFC 9457. The string lets the snackbar
     * show something useful, and the mapper reports it to telemetry.
     */
    data class Unknown(val code: String, val detail: String) : AuthError
}
