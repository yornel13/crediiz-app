package com.project.vortex.callsagent.presentation.clients

/**
 * The three top-level views the agent can switch between inside the
 * Clients tab. Each maps to a different data source and a different
 * card style. See `docs/CLIENTS_TAB_REDESIGN.md` for the rationale.
 */
enum class ClientsViewKind(val labelEs: String) {
    /** `status = PENDING`, ordered by `queueOrder`. */
    PENDIENTES(labelEs = "Pendientes"),

    /** Called within the last 24 h, any outcome. */
    RECIENTES(labelEs = "Recientes"),

    /** `status = INTERESTED`, indefinite. */
    INTERESADOS(labelEs = "Interesados"),
}
