package com.project.vortex.callsagent.common.enums

/**
 * Current state of a client in the sales funnel. One value per client at
 * a time, **global** across every agent working that client.
 *
 * Mirror of the backend 5-state model documented in
 * `calls-core/FRONTEND-ESTADOS.md` and `calls-core/docs/flujo-de-estados-cliente.md`.
 *
 * The status is **derived by the backend** from the full interaction history
 * (precedence rules by event date). The mobile app is **NOT** responsible for
 * deciding statuses — it emits call outcomes / actions and reflects the status
 * the backend returns. See `docs/MIGRACION-MODELO-5-ESTADOS.md`.
 *
 * Funnel height (high-water mark): PENDING < INTERESTED < CITED < CONVERTED.
 * An agent can only move *up*; REMOVED is a lateral, off-funnel state.
 */
enum class ClientStatus {
    /** Active, in queue. Includes freshly uploaded ("new") and called-without-outcome. */
    PENDING,

    /** Client expressed interest / requested information. Active. */
    INTERESTED,

    /** Client has a formal appointment scheduled (e.g. at the bank). Active. */
    CITED,

    /** Credit signed. Positive terminal state. The app hides/blocks it from the agenda. */
    CONVERTED,

    /**
     * Dropped out of the process. **Always carries a [RemovalReason]**.
     * Reactivable by admin, and revivable by a later agent advance.
     */
    REMOVED,
}
