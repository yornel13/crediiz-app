package com.project.vortex.callsagent.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isStaleData by viewModel.isStaleData.collectAsState()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedVisibility(visible = isStaleData) {
                StaleDataBanner(onDismiss = viewModel::dismissStaleBanner)
            }

            NavHost(
                navController = navController,
                startDestination = HomeTabs.CLIENTS,
                modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun StaleDataBanner(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Data may be out of date",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "We couldn't reach the server. Pull to refresh once you're back online.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}
