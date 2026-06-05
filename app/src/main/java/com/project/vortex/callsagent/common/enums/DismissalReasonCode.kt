package com.project.vortex.callsagent.common.enums

import androidx.annotation.StringRes
import com.project.vortex.callsagent.R

/**
 * Preset reason codes the agent can pick when dismissing a client.
 * The list lives mobile-only (the backend stores `reasonCode` as a
 * plain string) so we can grow it without a backend deploy.
 *
 * Each code carries a localized UI label ([labelRes], resolved at the
 * call site with `stringResource`). The wire format is `name`
 * (e.g. `"DUPLICATE"`).
 */
enum class DismissalReasonCode(@StringRes val labelRes: Int) {
    CORPORATE_NUMBER(labelRes = R.string.enum_dismissal_corporate),
    INVALID_DATA(labelRes = R.string.enum_dismissal_invalid_data),
    DUPLICATE(labelRes = R.string.enum_dismissal_duplicate),
    OPTOUT(labelRes = R.string.enum_dismissal_optout),
    OUT_OF_SCOPE(labelRes = R.string.enum_dismissal_out_of_scope),
    OTHER(labelRes = R.string.enum_dismissal_other),
}
