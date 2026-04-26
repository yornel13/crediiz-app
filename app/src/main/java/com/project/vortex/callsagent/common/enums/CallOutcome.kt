package com.project.vortex.callsagent.common.enums

enum class CallOutcome {
    INTERESTED,
    NOT_INTERESTED,
    NO_ANSWER,
    BUSY,
    INVALID_NUMBER,

    /**
     * Agent closed the credit sale on this call. Terminal: maps to
     * `ClientStatus.CONVERTED`, the auto-call session stops, and the
     * client surfaces in Recientes for 24 h with a "Sold" badge.
     */
    SOLD,
}
