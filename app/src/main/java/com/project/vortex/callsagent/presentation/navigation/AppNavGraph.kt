package com.project.vortex.callsagent.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.project.vortex.callsagent.presentation.autocall.SessionSummaryScreen
import com.project.vortex.callsagent.presentation.home.HomeScreen
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
) {
    val navController: NavHostController = rememberNavController()

    // After a real call ends, CallManager persists the InteractionEntity and
    // surfaces it via lastEndedCall. Observe and navigate to PostCall —
    // this runs while InCallActivity is still showing its "Call ended"
    // confirmation, so when InCallActivity finishes the user lands on
    // PostCall instead of seeing PreCall flash.
    val pendingEndedCall by callNavViewModel.endedCall.collectAsState()
    LaunchedEffect(pendingEndedCall) {
        val ended = pendingEndedCall ?: return@LaunchedEffect
        navController.navigate(Routes.postCall(ended.clientId, ended.interactionMobileSyncId)) {
            // Replace PreCall in the back stack so back from PostCall returns to Home.
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        callNavViewModel.consumeEndedCall()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onResolved = { loggedIn ->
                    val target = if (loggedIn) Routes.HOME else Routes.LOGIN
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
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
        ) {
            PreCallScreen(
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
            ),
        ) {
            PostCallScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Pop everything back to Home so the agent lands on the queue.
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onSavedNextInSession = { nextClientId ->
                    navController.navigate(Routes.preCall(nextClientId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
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
