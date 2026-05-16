package com.project.vortex.callsagent.common.enums

/**
 * Sub-classification of an INTERESTED client (the "thermometer" in
 * `calls-core/docs/HOW_IT_WORKS.md §4`).
 *
 * Only meaningful when [ClientStatus] is `INTERESTED`. The backend
 * resets this field to `null` when the client transitions to any other
 * status — the mobile app reflects that by reading the server state
 * after sync, never writing `null` itself.
 *
 * Visual conventions (matched in `ui/theme/StatusColors.kt`):
 *  - 🟦 [COLD]  — solo recibió la información del producto.
 *  - 🟧 [WARM]  — recibió cotización aproximada.
 *  - 🟥 [HOT]   — cotización formal bancaria o cita en banco.
 */
enum class InterestLevel {
    COLD,
    WARM,
    HOT,
}
