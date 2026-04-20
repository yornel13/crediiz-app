package com.project.vortex.callsagent.data.remote.interceptor

import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds the Bearer token to every outgoing request.
 * Requests to /auth/login are skipped (no token needed, and it wouldn't exist yet).
 *
 * We use runBlocking here because OkHttp's Interceptor API is synchronous.
 * The DataStore read is very fast (in-memory cache) so this is acceptable.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authPreferences: AuthPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip login endpoint — no token available yet
        if (request.url.encodedPath.endsWith("/auth/login")) {
            return chain.proceed(request)
        }

        val token = runBlocking { authPreferences.currentToken() }

        val authedRequest = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }

        return chain.proceed(authedRequest)
    }
}
