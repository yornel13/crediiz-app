package com.project.vortex.callsagent.domain.result

/**
 * Two-channel result type for operations that can fail with a typed
 * domain error. Use it instead of [kotlin.Result] when the call-site
 * needs to discriminate failure modes — e.g. show a different
 * snackbar for `MissingReason` vs `Network`.
 *
 * Naming: deliberately **not** `Result` to avoid collision with
 * Kotlin's stdlib `Result`, which the project already uses elsewhere
 * for fire-and-forget paths. Both can coexist:
 * - `kotlin.Result<Unit>` for `refreshAssigned` / `refreshAgenda`
 *   (failures are logged and swallowed).
 * - [OperationResult] for writes whose UX MUST surface the failure
 *   reason to the agent (status changes, interest-level updates).
 *
 * **Project invariant:** every repository operation that performs a
 * local rollback on HTTP failure MUST return an [OperationResult] and
 * the caller MUST handle [Failure] explicitly (snackbar, retry,
 * etc.). Silent rollback is always a bug.
 */
sealed interface OperationResult<out T, out E> {
    data class Success<T>(val value: T) : OperationResult<T, Nothing>
    data class Failure<E>(val error: E) : OperationResult<Nothing, E>
}

/**
 * Convenience for call-sites that only need to react to errors.
 * Extension form so it stays `inline` (cheap suspend usage) without
 * tripping Kotlin's "virtual member" rule on the sealed interface.
 */
inline fun <T, E> OperationResult<T, E>.onFailure(
    block: (E) -> Unit,
): OperationResult<T, E> {
    if (this is OperationResult.Failure) block(error)
    return this
}

/** Symmetric counterpart of [onFailure]. */
inline fun <T, E> OperationResult<T, E>.onSuccess(
    block: (T) -> Unit,
): OperationResult<T, E> {
    if (this is OperationResult.Success) block(value)
    return this
}
