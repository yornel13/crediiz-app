package com.project.vortex.callsagent.common.enums

/**
 * Origin of a status change in the client's status history
 * (`GET /clients/:id/status-history`). Lets the timeline label *how* a
 * change happened — distinct from *who* (`changedByRole`).
 *
 * Mirror of the backend `source` enum. Unknown values map to `null` at the
 * mapper layer (forward-compatible with new sources).
 */
enum class StatusChangeSource {
    /** Initial state when the client was loaded into the system. */
    INITIAL_LOAD,

    /** Derived by the backend from a call outcome (threshold / quorum). */
    CALL_OUTCOME,

    /** Agent moved the client without a call (`agent-status-change`). */
    AGENT_OUT_OF_BAND,

    /** Admin changed the status manually from the panel. */
    ADMIN_MANUAL,

    /** Admin reactivated a removed client. */
    ADMIN_REACTIVATE,

    /** Agent removed (dismissed) the client. */
    AGENT_DISMISSAL,

    /** A previous agent dismissal was undone. */
    AGENT_DISMISSAL_UNDONE,
}
