package com.project.vortex.callsagent.common.enums

/**
 * Current state of a client in the sales funnel. One value per client at
 * a time.
 *
 * Mirror of the backend enum in
 * `calls-core/src/common/enums/client-status.enum.ts`. Transitions are
 * derived from [CallOutcome] via `ClientsService.changeStatus` on the
 * backend; the mobile app is read-only on this dimension (emits the
 * outcome, refetches the resulting status).
 */
enum class ClientStatus {
    /** Never called. Default at creation. */
    PENDING,

    /**
     * Called at least once without reaching a terminal outcome.
     * Set by the backend after `NO_ANSWER`, `BUSY`, or `WRONG_NUMBER`
     * (under threshold).
     */
    IN_PROGRESS,

    /**
     * Client expressed interest. Pairs with `Client.interestLevel`
     * (COLD/WARM/HOT) on the backend; mobile UI for that thermometer
     * lands in a follow-up tier (see `HOW_IT_WORKS_ALIGNMENT.md § T-A`).
     */
    INTERESTED,

    /** Sale closed. Terminal. */
    CONVERTED,

    /** Client explicitly declined. Admin can revive via reassign. */
    REJECTED,

    /**
     * Number confirmed unreachable after `wrongNumberCount` reaches the
     * backend threshold (default 3 consecutive `WRONG_NUMBER` outcomes).
     * Replaces the previous `INVALID_NUMBER`.
     */
    UNREACHABLE,

    /** Legal opt-out / blocklist. Set via outcome `ANSWERED_OPT_OUT` or admin manual. */
    DO_NOT_CALL,

    /**
     * Agent-initiated dismissal. Soft state — admin can override
     * (reactivate / reassign). See `docs/CLIENT_DISMISSAL.md`.
     */
    DISMISSED,
}
