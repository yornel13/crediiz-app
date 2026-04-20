package com.project.vortex.callsagent.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.project.vortex.callsagent.presentation.agenda.AgendaScreen
import com.project.vortex.callsagent.presentation.clients.ClientsScreen
import com.project.vortex.callsagent.presentation.navigation.HomeTabs
import com.project.vortex.callsagent.presentation.settings.SettingsScreen

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(HomeTabs.CLIENTS, "Clients", Icons.Filled.Person),
    TabItem(HomeTabs.AGENDA, "Agenda", Icons.Filled.CalendarMonth),
    TabItem(HomeTabs.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onClientSelected: (String) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    // Pop anything stacked on top of the start destination.
                                    popUpTo(HomeTabs.CLIENTS) { saveState = true }
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeTabs.CLIENTS,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(HomeTabs.CLIENTS) {
                ClientsScreen(
                    onClientSelected = onClientSelected,
                    onStartAutoCall = {
                        // Phase 5: auto-call uses the same destination + a flag.
                        // For now jump to the first client's pre-call.
                    },
                )
            }
            composable(HomeTabs.AGENDA) {
                AgendaScreen(onFollowUpSelected = onClientSelected)
            }
            composable(HomeTabs.SETTINGS) {
                SettingsScreen(onLoggedOut = onLogout)
            }
        }
    }
}
