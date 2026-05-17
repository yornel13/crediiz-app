package com.project.vortex.callsagent.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.project.vortex.callsagent.domain.auth.SessionInvalidationReason
import com.project.vortex.callsagent.presentation.autocall.SessionSummaryScreen
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.presentation.home.HomeScreen
import com.project.vortex.callsagent.presentation.login.LoginReason
import com.project.vortex.callsagent.presentation.login.LoginScreen
import com.project.vortex.callsagent.presentation.postcall.PostCallScreen
import com.project.vortex.callsagent.presentation.precall.PreCallScreen
import com.project.vortex.callsagent.presentation.splash.SplashScreen

/**
 * Root navigation graph. Always starts at [Routes.SPLASH], which resolves
 * the auth state and bounces to either [Routes.LOGIN] or [Routes.HOME].
 * That avoids the flash-of-login-screen we'd get if we picked
 * `startDestination` synchronously from a still-loading `Flow`.
 */
@Composable
fun AppNavGraph(
    callNavViewModel: CallNavigationViewModel = hiltViewModel(),
    sessionWatcher: SessionWatcherViewModel = hiltViewModel(),
) {
    val navController: NavHostController = rememberNavController()
    val ctx = LocalContext.current

    // Save-failure surface — CallController emits a non-null message
    // here when post-call persistence fails. We Toast it so the agent
    // sees WHY the call disappeared instead of just observing
    // "no aparece en recientes". The Toast is long so a screenshot
    // gives enough info to diagnose without logcat.
    val saveError by callNavViewModel.saveError.collectAsState()
    LaunchedEffect(saveError) {
        val msg = saveError ?: return@LaunchedEffect
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        callNavViewModel.consumeSaveError()
    }

    // Server killed the session (single-active-session displaced this
    // device, admin revoked, agent logged out elsewhere) → bounce to
    // /login with a copy explaining what happened. The cleanup
    // (clearing the token + local data) already ran in the watcher
    // ViewModel before the event reached us.
    LaunchedEffect(Unit) {
        sessionWatcher.navigateToLogin.collect { reason ->
            val wireReason = when (reason) {
                SessionInvalidationReason.Invalidated -> LoginReason.INVALIDATED
                SessionInvalidationReason.Expired -> LoginReason.EXPIRED
            }
            navController.navigate(Routes.login(wireReason)) {
                // Clear the back stack so the agent can't go "Back" into
                // the now-unauthenticated app surfaces.
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // After a real call ends, CallManager persists the InteractionEntity and
    // surfaces it via lastEndedCall. Observe and navigate to PostCall —
    // this runs while InCallActivity is still showing its "Call ended"
    // confirmation, so when InCallActivity finishes the user lands on
    // PostCall instead of seeing PreCall flash.
    val pendingEndedCall by callNavViewModel.endedCall.collectAsState()
    LaunchedEffect(pendingEndedCall) {
        val ended = pendingEndedCall ?: return@LaunchedEffect
        navController.navigate(
            Routes.postCall(
                clientId = ended.clientId,
                interactionId = ended.interactionMobileSyncId,
                prefilledOutcome = ended.suggestedOutcome,
                allowedOutcomes = ended.allowedOutcomes,
                reasonLabel = ended.reasonLabel,
            ),
        ) {
            // Replace PreCall in the back stack so back from PostCall returns to Home.
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        callNavViewModel.consumeEndedCall()
    }

    // Phase 7.5 — orphan-call recovery. If the app was killed between a
    // call ending and the agent finishing Post-Call, on next launch we
    // surface the unconfirmed interaction so the agent can wrap up.
    // We wait until the home destination is in the graph before
    // navigating, otherwise the splash → home transition would race
    // with our navigation.
    val orphanInteraction by callNavViewModel.orphanInteraction.collectAsState()
    LaunchedEffect(orphanInteraction) {
        val orphan = orphanInteraction ?: return@LaunchedEffect
        // Only fire once we're past the splash. Splash → Home navigation
        // sets popUpTo(SPLASH inclusive), so once Home is the current
        // destination we know the app is settled.
        navController.currentBackStackEntry ?: return@LaunchedEffect
        navController.navigate(
            Routes.postCall(orphan.clientId, orphan.interactionMobileSyncId),
        ) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        callNavViewModel.consumeOrphanInteraction()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onResolved = { loggedIn ->
                    val target = if (loggedIn) Routes.HOME else Routes.LOGIN_BASE
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Routes.LOGIN,
            arguments = listOf(
                navArgument("reason") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN_BASE) { inclusive = true }
                    }
                },
                reason = entry.arguments?.getString("reason"),
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN_BASE) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onClientSelected = { clientId ->
                    navController.navigate(Routes.preCall(clientId))
                },
            )
        }

        composable(
            route = Routes.PRE_CALL,
            arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
        ) { entry ->
            val clientId = entry.arguments?.getString("clientId")
                ?: error("PreCall route entered without a clientId arg")
            PreCallScreen(
                clientId = clientId,
                onBack = { navController.popBackStack() },
                onSkipToNext = { nextClientId ->
                    navController.navigate(Routes.preCall(nextClientId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onSkipToSummary = {
                    navController.navigate(Routes.SESSION_SUMMARY) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onExitAutoCall = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = Routes.POST_CALL,
            arguments = listOf(
                navArgument("clientId") { type = NavType.StringType },
                navArgument("interactionId") { type = NavType.StringType },
                navArgument("prefilledOutcome") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("allowedOutcomes") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("reasonLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            // Captured once per PostCall mount — drives the routing
            // branch on PostCall save. The PreCall route is full-screen
            // and bypasses the Home shell's list/detail scaffold; we
            // only want to take that path on compact phones. On tablet
            // / split mode the agent should return to the shared
            // Home shell so the next client opens IN the detail pane,
            // not in a separate full-screen route that hides the list.
            val isSplitMode = WindowSize.isWideWidth

            PostCallScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Pop everything back to Home so the agent lands on the queue.
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onSavedNextInSession = { nextClientId ->
                    if (isSplitMode) {
                        // Wide / split: return to Home. ClientsScreenAdaptive
                        // observes the AutoCallOrchestrator session and will
                        // route the detail pane to `nextClientId` as soon as
                        // its composition resumes — keeping the list +
                        // detail layout intact.
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    } else {
                        // Compact: PreCall lives as its own full-screen
                        // route. Navigate directly to the next one.
                        navController.navigate(Routes.preCall(nextClientId)) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onSavedSessionComplete = {
                    navController.navigate(Routes.SESSION_SUMMARY) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }

        composable(Routes.SESSION_SUMMARY) {
            SessionSummaryScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

        // Placeholder for Phase 3 — the real In-Call lives in InCallActivity.
        composable(Routes.IN_CALL) { /* TODO: Phase 3 */ }
    }
}
