package com.project.vortex.callsagent.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.project.vortex.callsagent.presentation.home.HomeScreen
import com.project.vortex.callsagent.presentation.login.LoginScreen

/**
 * Root navigation graph. Picks the start destination based on whether
 * the agent is already logged in.
 */
@Composable
fun AppNavGraph(
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val navController: NavHostController = rememberNavController()
    val isLoggedIn by rootViewModel.isLoggedIn.collectAsState()

    // While we wait for the first emission we default to Login;
    // RootViewModel flips the flag quickly once DataStore resolves.
    val startDestination = if (isLoggedIn == true) Routes.HOME else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
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

        // Placeholder destinations for phase 5 — added so navigate() compiles today.
        composable(Routes.PRE_CALL) { /* TODO: Phase 5 */ }
        composable(Routes.IN_CALL) { /* TODO: Phase 5 */ }
        composable(Routes.POST_CALL) { /* TODO: Phase 5 */ }
    }
}
