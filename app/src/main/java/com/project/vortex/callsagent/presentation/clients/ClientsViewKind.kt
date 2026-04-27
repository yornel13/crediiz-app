package com.project.vortex.callsagent.presentation.clients

/**
 * Top-level views inside the Clients tab. Two only:
 *
 * - **Pending** (`PENDIENTES`): cold queue (`status = PENDING`),
 *   split visually into "Untouched" + "Retry" sub-sections.
 * - **Recent** (`RECIENTES`): anything the agent touched in the
 *   last 24 h — calls of any outcome (including `SOLD` → "Sold"
 *   badge) and dismissals (with an Undo button).
 *
 * INTERESTED leads live in the Agenda tab. See
 * `docs/CLIENTS_TAB_REDESIGN.md` for the rationale.
 *
 * Note: enum names are kept in Spanish (`PENDIENTES`/`RECIENTES`)
 * for continuity with existing telemetry / docs. The UI label is
 * separate and currently English; Spanish strings will be added via
 * `values-es/strings.xml` in a later i18n pass.
 */
enum class ClientsViewKind(val label: String) {
    PENDIENTES(label = "Pending"),
    RECIENTES(label = "Recent"),
}
