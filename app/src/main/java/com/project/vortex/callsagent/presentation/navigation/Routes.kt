package com.project.vortex.callsagent.presentation.navigation

import android.net.Uri
import com.project.vortex.callsagent.common.enums.CallOutcome

/**
 * Top-level destinations of the app (root NavGraph).
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"

    const val PRE_CALL = "pre_call/{clientId}"
    fun preCall(clientId: String) = "pre_call/$clientId"

    const val IN_CALL = "in_call"

    /**
     * Post-Call. Required path args: `clientId` and `interactionId`.
     *
     * Optional query args (added by [postCall] when the call ended via
     * the SIP engine — orphan-recovery path leaves them blank and the
     * screen falls back to its legacy behavior):
     *
     *  - `prefilledOutcome` — name of the [CallOutcome] to pre-select.
     *  - `allowedOutcomes`  — comma-separated CallOutcome names; PostCall
     *    only renders these. If absent or blank, all 5 are shown.
     *  - `reasonLabel`      — short URL-encoded Spanish phrase shown
     *    under the outcome header (e.g. "Línea ocupada.").
     */
    const val POST_CALL =
        "post_call/{clientId}/{interactionId}" +
            "?prefilledOutcome={prefilledOutcome}" +
            "&allowedOutcomes={allowedOutcomes}" +
            "&reasonLabel={reasonLabel}"

    fun postCall(
        clientId: String,
        interactionId: String,
        prefilledOutcome: CallOutcome? = null,
        allowedOutcomes: List<CallOutcome> = emptyList(),
        reasonLabel: String? = null,
    ): String {
        val params = buildList {
            prefilledOutcome?.let { add("prefilledOutcome=${it.name}") }
            if (allowedOutcomes.isNotEmpty()) {
                add("allowedOutcomes=${allowedOutcomes.joinToString(",") { it.name }}")
            }
            reasonLabel?.takeIf { it.isNotBlank() }?.let {
                add("reasonLabel=${Uri.encode(it)}")
            }
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "post_call/$clientId/$interactionId$query"
    }

    /** Session recap shown when an auto-call queue is exhausted. */
    const val SESSION_SUMMARY = "session_summary"
}

/**
 * Tabs of the Home scaffold (nested graph).
 */
object HomeTabs {
    const val CLIENTS = "home/clients"
    const val AGENDA = "home/agenda"
    const val SETTINGS = "home/settings"
}
