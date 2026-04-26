package com.project.vortex.callsagent.common.enums

enum class ClientStatus {
    PENDING,
    INTERESTED,
    CONVERTED,
    REJECTED,
    INVALID_NUMBER,
    DO_NOT_CALL,

    /**
     * Agent-initiated dismissal. Soft state — admin can override
     * (reactivate / reassign). See `docs/CLIENT_DISMISSAL.md`.
     */
    DISMISSED,
}
