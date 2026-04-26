package com.project.vortex.callsagent.presentation.clients

/**
 * Top-level views inside the Clients tab. Two only:
 *
 * - **Pendientes**: cold queue (`status = PENDING`) ordered by
 *   `queueOrder`.
 * - **Recientes**: anything the agent touched in the last 24 h —
 *   calls of any outcome (including `SOLD` → "Sold" badge) and
 *   dismissals (with a Deshacer button).
 *
 * INTERESTED leads live in the Agenda tab (under Próximas if they
 * have a follow-up scheduled, or under "Sin agendar" if they don't).
 * See `docs/CLIENTS_TAB_REDESIGN.md` for the rationale.
 */
enum class ClientsViewKind(val labelEs: String) {
    PENDIENTES(labelEs = "Pendientes"),
    RECIENTES(labelEs = "Recientes"),
}
