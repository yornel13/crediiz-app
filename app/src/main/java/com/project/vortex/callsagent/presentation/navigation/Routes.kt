package com.project.vortex.callsagent.presentation.navigation

/**
 * Top-level destinations of the app (root NavGraph).
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Phase 5 destinations (stubs declared now so navigation compiles)
    const val PRE_CALL = "pre_call/{clientId}"
    fun preCall(clientId: String) = "pre_call/$clientId"

    const val IN_CALL = "in_call"
    const val POST_CALL = "post_call"
}

/**
 * Tabs of the Home scaffold (nested graph).
 */
object HomeTabs {
    const val CLIENTS = "home/clients"
    const val AGENDA = "home/agenda"
    const val SETTINGS = "home/settings"
}
