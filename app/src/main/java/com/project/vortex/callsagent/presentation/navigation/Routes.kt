package com.project.vortex.callsagent.presentation.navigation

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
     * Post-Call. `clientId` identifies the client whose call just ended;
     * `interactionId` is the local mobileSyncId of the InteractionEntity
     * created at call end (or by the dev "Simulate" button in debug builds).
     */
    const val POST_CALL = "post_call/{clientId}/{interactionId}"
    fun postCall(clientId: String, interactionId: String) =
        "post_call/$clientId/$interactionId"

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
