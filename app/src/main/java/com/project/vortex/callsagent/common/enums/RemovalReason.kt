package com.project.vortex.callsagent.common.enums

/**
 * Reason a client was moved to [ClientStatus.REMOVED]. A `removalReason` is
 * **mandatory** whenever the status is `REMOVED` (backend rejects with `400`
 * `CLIENT_REASON_REQUIRED` otherwise).
 *
 * Mirror of the backend `RemovalReason` enum documented in
 * `calls-core/FRONTEND-ESTADOS.md`. Replaces the old mobile-only
 * `DismissalReasonCode`.
 *
 * Bare enum by design: UI labels are resolved in the presentation layer
 * (a centralized `@StringRes` mapper), keeping Android resources out of the
 * domain enum. See `docs/MIGRACION-MODELO-5-ESTADOS.md`.
 *
 * Assignment (see `flujo-de-estados-cliente.md §7`):
 *  - [NOT_INTERESTED] / [UNREACHABLE]: auto after threshold (5), or manual.
 *  - [DO_NOT_CALL] / [WRONG_NUMBER] / [HAS_LOAN] / [DECEASED]: auto via 2-agent quorum, or manual.
 *  - [NOT_APPLICABLE] / [OTHER]: manual only.
 */
enum class RemovalReason {
    /** No le interesa. */
    NOT_INTERESTED,

    /** No localizable. */
    UNREACHABLE,

    /** Molesto / no llamar más. */
    DO_NOT_CALL,

    /** Número equivocado. */
    WRONG_NUMBER,

    /** Ya tiene préstamo. */
    HAS_LOAN,

    /** Fallecido. */
    DECEASED,

    /** No aplica. */
    NOT_APPLICABLE,

    /** Otro (texto libre en `reason`). */
    OTHER,
}
