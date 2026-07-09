package com.project.vortex.callsagent.data.sync

/**
 * Classification of the session-vs-data-owner identity check that gates
 * every sync push. Pure policy — callers decide the action per verdict.
 */
enum class IdentityCheck {
    /** Session agent matches the data owner — push freely. */
    CONSISTENT,

    /**
     * Data predates ownership tracking (owner never recorded) but a session
     * is active. The rows in Room were necessarily produced by the logged-in
     * agent (before this policy existed, logout always wiped the DB), so the
     * caller should adopt: record the session agent as owner and proceed.
     */
    ADOPT_SESSION,

    /**
     * Push must not happen: either nobody is logged in (a WorkManager retry
     * outliving the session) or the session agent differs from the data
     * owner (a stale in-flight worker crossing a login). Pushing here would
     * attribute agent A's rows to agent B — the server infers authorship
     * from the JWT — permanently corrupting the append-only audit trail.
     */
    BLOCKED,
}

/**
 * Decides whether locally-stored PENDING rows may be pushed under the
 * current session. Exists because sync runs on WorkManager: cancelAll()
 * does not abort in-flight workers and retries back off for minutes, so a
 * worker can execute across a logout/login boundary with a different JWT.
 */
object SyncIdentityPolicy {

    fun evaluate(sessionAgentId: String?, dataOwnerAgentId: String?): IdentityCheck = when {
        sessionAgentId.isNullOrBlank() -> IdentityCheck.BLOCKED
        dataOwnerAgentId == null -> IdentityCheck.ADOPT_SESSION
        dataOwnerAgentId == sessionAgentId -> IdentityCheck.CONSISTENT
        else -> IdentityCheck.BLOCKED
    }
}
