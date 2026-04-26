package com.project.vortex.callsagent.common.enums

/**
 * Preset reason codes the agent can pick when dismissing a client.
 * The list lives mobile-only (the backend stores `reasonCode` as a
 * plain string) so we can grow it without a backend deploy.
 *
 * Each code carries a Spanish label — these are real UI literals
 * the agent reads. The wire format is `name` (e.g. `"DUPLICATE"`).
 */
enum class DismissalReasonCode(val labelEs: String) {
    CORPORATE_NUMBER(labelEs = "Corporativo"),
    INVALID_DATA(labelEs = "Datos errados"),
    DUPLICATE(labelEs = "Duplicado"),
    OPTOUT(labelEs = "Opt-out"),
    OUT_OF_SCOPE(labelEs = "No aplica"),
    OTHER(labelEs = "Otro"),
}
