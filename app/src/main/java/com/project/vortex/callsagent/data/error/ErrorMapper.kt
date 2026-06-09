package com.project.vortex.callsagent.data.error

import com.project.vortex.callsagent.common.error.ErrorCodes
import com.project.vortex.callsagent.common.telemetry.TelemetryLogger
import com.project.vortex.callsagent.data.remote.dto.ProblemDetails
import com.project.vortex.callsagent.domain.error.AuthError
import com.project.vortex.callsagent.domain.error.ClientError
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps an arbitrary [Throwable] thrown by Retrofit/OkHttp to a typed
 * domain error following the RFC 9457 contract emitted by `calls-core`
 * (see `calls-core/docs/ERROR_CONTRACT.md`).
 *
 * The mapper **always** tries to parse the error body as
 * [ProblemDetails] first and branches on [ProblemDetails.code]. Only
 * when:
 *  - the throwable isn't an [HttpException] (network, parsing),
 *  - the response has no body, or
 *  - the body is malformed JSON,
 *
 * does it fall back to a typed default ([AuthError.Network] /
 * [ClientError.Network] for IO, `Unknown(MALFORMED, …)` otherwise).
 *
 * Codes outside [ErrorCodes] are also reported via [TelemetryLogger]
 * so a backend drift surfaces before users complain.
 */
@Singleton
class ErrorMapper @Inject constructor(
    moshi: Moshi,
    private val telemetry: TelemetryLogger,
) {
    private val problemAdapter = moshi.adapter(ProblemDetails::class.java)

    // ─── Auth ───────────────────────────────────────────────────────────

    fun toAuthError(t: Throwable): AuthError {
        if (t is IOException) return AuthError.Network
        if (t !is HttpException) {
            return AuthError.Unknown(
                code = "EXCEPTION",
                detail = t.message ?: t::class.java.simpleName,
            )
        }
        val problem = t.parseProblem()
            ?: return AuthError.Unknown(
                code = ErrorCodes.MALFORMED,
                detail = "Server returned non-RFC9457 error body",
            )
        return when (problem.code) {
            ErrorCodes.AUTH_INVALID_CREDENTIALS -> AuthError.InvalidCredentials
            ErrorCodes.AUTH_ACCOUNT_DISABLED -> AuthError.AccountDisabled
            ErrorCodes.AUTH_DEVICE_REQUIRED -> AuthError.DeviceRequired
            ErrorCodes.AUTH_SESSION_INVALIDATED,
            ErrorCodes.AUTH_TOKEN_MISSING_SESSION -> AuthError.SessionExpired
            else -> {
                telemetry.unknownErrorCode(problem.code, problem.detail, problem.instance)
                AuthError.Unknown(problem.code, problem.detail)
            }
        }
    }

    // ─── Clients ────────────────────────────────────────────────────────

    fun toClientError(t: Throwable): ClientError {
        if (t is IOException) return ClientError.Network
        if (t !is HttpException) {
            return ClientError.Unknown(
                code = "EXCEPTION",
                detail = t.message ?: t::class.java.simpleName,
            )
        }
        val problem = t.parseProblem()
            ?: return ClientError.Unknown(
                code = ErrorCodes.MALFORMED,
                detail = "Server returned non-RFC9457 error body",
            )
        return when (problem.code) {
            ErrorCodes.CLIENT_REASON_REQUIRED ->
                ClientError.ReasonRequired(toStatus = problem.toStatus ?: "")
            ErrorCodes.CLIENT_NOT_ASSIGNED -> ClientError.NotAssigned
            ErrorCodes.CLIENT_TARGET_NOT_ALLOWED ->
                ClientError.TargetNotAllowed(toStatus = problem.toStatus ?: "")
            ErrorCodes.CLIENT_NOT_FOUND,
            ErrorCodes.RESOURCE_NOT_FOUND -> ClientError.NotFound
            ErrorCodes.RESOURCE_CONFLICT,
            ErrorCodes.CLIENT_DUPLICATE_KEY -> ClientError.Conflict
            ErrorCodes.AUTH_SESSION_INVALIDATED,
            ErrorCodes.AUTH_TOKEN_MISSING_SESSION -> ClientError.SessionExpired
            else -> {
                telemetry.unknownErrorCode(problem.code, problem.detail, problem.instance)
                ClientError.Unknown(problem.code, problem.detail)
            }
        }
    }

    // ─── Internal helpers ───────────────────────────────────────────────

    /**
     * Parses the Retrofit error body as [ProblemDetails], returning
     * `null` on any failure (no body, malformed JSON, missing required
     * fields). The caller falls back to a `MALFORMED` sentinel and
     * reports it to telemetry.
     *
     * Safe to call multiple times in theory, but the OkHttp body is
     * consumed by `.string()` — call once per mapping.
     */
    private fun HttpException.parseProblem(): ProblemDetails? {
        val body = try {
            response()?.errorBody()?.string()
        } catch (_: Throwable) {
            null
        }
        if (body.isNullOrBlank()) {
            telemetry.malformedErrorBody(code(), instance = "<unknown>", snippet = "")
            return null
        }
        return try {
            problemAdapter.fromJson(body).also { parsed ->
                if (parsed == null) {
                    telemetry.malformedErrorBody(code(), "<unknown>", body)
                }
            }
        } catch (e: JsonDataException) {
            telemetry.malformedErrorBody(code(), "<unknown>", body)
            null
        } catch (_: Throwable) {
            telemetry.malformedErrorBody(code(), "<unknown>", body)
            null
        }
    }
}
