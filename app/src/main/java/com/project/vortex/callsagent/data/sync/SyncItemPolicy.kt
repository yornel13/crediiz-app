package com.project.vortex.callsagent.data.sync

/**
 * Pure policy for how [SyncManager] batches the outbox and which per-item
 * server errors are terminal.
 *
 * Why batching: the backend (NestJS/Express) rejects JSON bodies over its
 * default 100 KB limit with a 413 BEFORE processing anything, and the
 * mobile read timeout is 30 s. Pushing the whole outbox in one request
 * means that once a device accumulates enough PENDING rows, every sync
 * fails as a whole, nothing is ever marked SYNCED, the outbox keeps
 * growing and the agent's calls stop reaching the server (the admin
 * dashboard's call counter freezes). Small batches keep each request
 * bounded and let every batch commit its own progress.
 *
 * Why terminal errors: when the admin hard-deletes clients (e.g. a full
 * reload of the client base), rows that reference them can never succeed —
 * the server answers `error` for them on every push. Left PENDING, they
 * are re-sent forever and inflate every subsequent request.
 */
object SyncItemPolicy {

    /**
     * Max records per request. Notes carry free text, so this is sized for
     * the heaviest category to stay well under the 100 KB body limit and
     * the 30 s read timeout.
     */
    const val BATCH_SIZE = 25

    /**
     * True when a per-item `error` from the server will never succeed on a
     * retry, so the row must leave the outbox instead of being re-pushed.
     *
     * - "Client Not Found Exception": the client was deleted server-side.
     *   For interactions the server has ALREADY stored the call (the upsert
     *   runs before the client lookup), so the call still counts.
     * - "Follow-up not found or already completed": nothing left to complete.
     *
     * Anything else (unknown/transient) stays PENDING and is retried.
     */
    fun isPermanentFailure(error: String?): Boolean {
        val e = error?.lowercase() ?: return false
        if (!e.contains("not found")) return false
        return e.contains("client") || e.contains("follow-up")
    }
}
