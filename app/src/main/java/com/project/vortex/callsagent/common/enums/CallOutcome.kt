package com.project.vortex.callsagent.common.enums

/**
 * Result of a single call interaction. Verb-form (events, not states) so
 * they are clearly distinct from [ClientStatus] (which describes where the
 * client is in the funnel right now).
 *
 * Mirror of the backend enum in
 * `calls-core/src/common/enums/call-outcome.enum.ts`. Any drift causes
 * `POST /sync/interactions` to reject with `400` because the server
 * validates with `@IsEnum(CallOutcome)` strict.
 *
 * The mapping from outcome → resulting [ClientStatus] is centralized on
 * the backend (`ClientsService.changeStatus`). The mobile app is **NOT**
 * responsible for deciding statuses — it emits the outcome and refetches
 * the client after a successful sync.
 */
enum class CallOutcome {
    /** Call connected, client expressed interest → INTERESTED (COLD by default). */
    ANSWERED_INTERESTED,

    /** Call connected, client declined → REJECTED. */
    ANSWERED_NOT_INTERESTED,

    /** Call connected, legal opt-out request → DO_NOT_CALL. Immediate. */
    ANSWERED_OPT_OUT,

    /** Call connected, sale closed → CONVERTED. Terminal — auto-call stops. */
    ANSWERED_SOLD,

    /** Call did not connect — no answer. Moves PENDING → IN_PROGRESS. */
    NO_ANSWER,

    /** Call did not connect — busy line. Moves PENDING → IN_PROGRESS. */
    BUSY,

    /**
     * Number does not work / wrong person / disconnected.
     * Increments `wrongNumberCount`. After threshold (3 by default) the
     * client moves to UNREACHABLE; otherwise stays in IN_PROGRESS.
     */
    WRONG_NUMBER,
}
