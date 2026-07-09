package com.project.vortex.callsagent.domain.repository

/**
 * Re-hydrates the local activity cache (notes + call interactions) for one
 * client from the server history. Exists because notes/interactions are
 * push-only in the sync cycle: after the local DB is wiped (agent change,
 * fresh install, destructive migration) the timeline would otherwise lose
 * all server-acknowledged history forever.
 */
interface ClientActivityRepository {

    /**
     * Fetch the server-side activity for [clientId] and merge it into Room
     * (insert-if-absent — never overwrites local rows). Best-effort by
     * contract: callers treat failure (offline / 403 after reassignment) as
     * "timeline shows local data only", not as an error state.
     */
    suspend fun hydrate(clientId: String): Result<Unit>
}
