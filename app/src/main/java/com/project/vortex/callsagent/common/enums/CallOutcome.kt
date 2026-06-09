package com.project.vortex.callsagent.common.enums

/**
 * Result of a single call interaction. Verb-form (events, not states) so
 * they are clearly distinct from [ClientStatus] (where the client is in the
 * funnel right now).
 *
 * Mirror of the backend enum (`CallOutcome`) documented in
 * `calls-core/FRONTEND-ESTADOS.md`. Any drift causes `POST /sync/interactions`
 * to reject with `400` (the server validates with `@IsEnum(CallOutcome)` strict).
 *
 * The mapping outcome → resulting [ClientStatus] is centralized on the backend
 * (thresholds, quorum, precedence). The mobile app is **NOT** responsible for
 * deciding statuses — it emits the outcome and refetches after sync.
 *
 * Effect summary (see `flujo-de-estados-cliente.md §10`):
 *  - No contact (NO_ANSWER, BUSY, OUT_OF_SERVICE, VOICEMAIL): stays PENDING;
 *    5 without contact → REMOVED (UNREACHABLE).
 *  - INTERESTED → INTERESTED · SCHEDULED → CITED · SOLD → CONVERTED.
 *  - NOT_INTERESTED: stays PENDING; 5 → REMOVED (NOT_INTERESTED).
 *  - DO_NOT_CALL / WRONG_NUMBER / HAS_LOAN / DECEASED: REMOVED via 2-agent quorum.
 *  - NOT_APPLICABLE: REMOVED (NOT_APPLICABLE).
 */
enum class CallOutcome {
    /** Call did not connect — no answer. No-contact bucket. */
    NO_ANSWER,

    /** Call did not connect — busy line. No-contact bucket. */
    BUSY,

    /** Number out of service. No-contact bucket. */
    OUT_OF_SERVICE,

    /** Reached voicemail / answering machine. No-contact bucket. */
    VOICEMAIL,

    /** Connected, client expressed interest → INTERESTED. */
    INTERESTED,

    /** Connected, appointment scheduled → CITED. */
    SCHEDULED,

    /** Connected, sale closed → CONVERTED. Terminal advance. */
    SOLD,

    /** Connected, client not interested. Stays PENDING until threshold → REMOVED. */
    NOT_INTERESTED,

    /** Connected, "do not call" / annoyed. Hard reason — REMOVED via quorum. */
    DO_NOT_CALL,

    /** Wrong number / wrong person. Hard reason — REMOVED via quorum. */
    WRONG_NUMBER,

    /** Already has a loan. Hard reason — REMOVED via quorum. */
    HAS_LOAN,

    /** Client deceased. Hard reason — REMOVED via quorum. */
    DECEASED,

    /** Does not apply. REMOVED (NOT_APPLICABLE), manual. */
    NOT_APPLICABLE,
}
