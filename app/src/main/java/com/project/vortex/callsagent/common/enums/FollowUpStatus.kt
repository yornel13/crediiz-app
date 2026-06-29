package com.project.vortex.callsagent.common.enums

/**
 * Lifecycle of a follow-up. Mirrors the backend's 4-state model.
 *
 *  - [PENDING]   — scheduled and still due.
 *  - [EXPIRED]   — 1h past the scheduled time OR the (Panama) day rolled over
 *                  without being handled. Still actionable: shown as "Vencido"
 *                  and can be called/completed any time.
 *  - [COMPLETED] — the agent called (answered or not). UI label: "Cumplido".
 *  - [CANCELLED] — superseded by a reschedule, or the client was dropped.
 */
enum class FollowUpStatus {
    PENDING,
    EXPIRED,
    COMPLETED,
    CANCELLED,
}
