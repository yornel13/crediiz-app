package com.project.vortex.callsagent.common.enums

/**
 * Preset reason codes the agent can pick when dismissing a client.
 * The list lives mobile-only (the backend stores `reasonCode` as a
 * plain string) so we can grow it without a backend deploy.
 *
 * Each code carries a UI label (English for now; Spanish translations
 * land later via `values-es/strings.xml`). The wire format is `name`
 * (e.g. `"DUPLICATE"`).
 */
enum class DismissalReasonCode(val label: String) {
    CORPORATE_NUMBER(label = "Corporate"),
    INVALID_DATA(label = "Bad data"),
    DUPLICATE(label = "Duplicate"),
    OPTOUT(label = "Opt-out"),
    OUT_OF_SCOPE(label = "Out of scope"),
    OTHER(label = "Other"),
}
