package com.project.vortex.callsagent.data.remote.interceptor

import com.project.vortex.callsagent.domain.auth.SessionEventBus
import com.project.vortex.callsagent.domain.auth.SessionInvalidationReason
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inspects every response and, on `401`, distinguishes between:
 *  - server-side session kill (`"Session has been invalidated"`)
 *    → emits [SessionInvalidationReason.Invalidated]
 *  - any other 401 (typically expired JWT)
 *    → emits [SessionInvalidationReason.Expired]
 *
 * The actual logout cleanup + navigation lives in the app shell,
 * subscribed to [SessionEventBus]. This interceptor is intentionally
 * dumb: detect, classify, publish.
 *
 * Skips `/auth/login` so a wrong password (which legitimately returns
 * 401) does NOT punt the user out of a valid prior session.
 *
 * Must be installed AFTER [AuthInterceptor] so the Bearer token is
 * present on the request when it reaches the wire — otherwise every
 * authenticated call would 401 spuriously.
 */
@Singleton
class SessionInvalidationInterceptor @Inject constructor(
    private val sessionEventBus: SessionEventBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != HTTP_UNAUTHORIZED) return response
        if (request.url.encodedPath.endsWith("/auth/login")) return response

        // peekBody clones up to N bytes WITHOUT consuming the response
        // body, so downstream readers (Retrofit's converter, HttpException
        // builders) still see the original payload.
        val peeked = runCatching { response.peekBody(PEEK_LIMIT_BYTES).string() }
            .getOrDefault("")

        val reason = if (INVALIDATED_MARKER in peeked) {
            SessionInvalidationReason.Invalidated
        } else {
            SessionInvalidationReason.Expired
        }
        sessionEventBus.publish(reason)
        return response
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val PEEK_LIMIT_BYTES = 2048L
        private const val INVALIDATED_MARKER = "Session has been invalidated"
    }
}
