package com.project.vortex.callsagent.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.domain.call.CallReadiness
import com.project.vortex.callsagent.presentation.agenda.AgendaScreen
import com.project.vortex.callsagent.presentation.clients.ClientsScreen
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.presentation.navigation.HomeTabs
import com.project.vortex.callsagent.presentation.settings.SettingsScreen
import com.project.vortex.callsagent.ui.components.CallReadinessBanner

private data class TabItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(HomeTabs.CLIENTS, R.string.home_tab_clients, Icons.Filled.Person),
    TabItem(HomeTabs.AGENDA, R.string.home_tab_agenda, Icons.Filled.CalendarMonth),
    TabItem(HomeTabs.SETTINGS, R.string.home_tab_settings, Icons.Filled.Settings),
)

/**
 * Adaptive Home shell.
 *
 * Hand-rolled per-breakpoint rather than wrapped in
 * `NavigationSuiteScaffold` — the scaffold doesn't let us control the
 * vertical alignment of its items, and Gmail-style chrome wants the
 * navigation block centered. We pay a small amount of duplication in
 * exchange for full layout control.
 *
 * - **Compact** (phones): `NavigationBar` at the bottom (legacy).
 * - **Medium / Expanded**: `NavigationRail` on the left, items
 *   vertically centered. No expanded-drawer mode — the rail is
 *   permanently collapsed (decision: agents work inside the panes,
 *   so a wider drawer was stealing horizontal space the list/detail
 *   scaffold needs more).
 */
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
    val callReadiness by viewModel.callReadiness.collectAsState()
    val showReadinessBanner = callReadiness !is CallReadiness.Ready &&
        callReadiness !is CallReadiness.Unknown

    var clientsScrollToTopTick by remember { mutableIntStateOf(0) }
    var agendaScrollToTopTick by remember { mutableIntStateOf(0) }

    val isWide = WindowSize.isWideWidth
    // NOTE: the rail/drawer toggle was removed. The side navigation
    // is always rendered as a collapsed `NavigationRail` — Crediiz
    // agents work primarily inside the content panes, not the chrome,
    // so a permanently visible drawer was stealing horizontal real
    // estate from the list/detail layout without adding affordances
    // the agents actually use.

    val selectedRoute: (String) -> Boolean = { route ->
        backStackEntry?.destination?.hierarchy?.any { it.route == route } == true
    }
    val onTabClick: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(HomeTabs.CLIENTS) { saveState = true }
            }
        } else {
            when (route) {
                HomeTabs.CLIENTS -> clientsScrollToTopTick++
                HomeTabs.AGENDA -> agendaScrollToTopTick++
            }
        }
    }

    if (!isWide) {
        // Compact: bottom navigation (legacy behavior, never seen in
        // production since Crediiz distributes tablets — kept for
        // emulator/preview).
        CompactScaffold(
            tabs = tabs,
            selectedRoute = selectedRoute,
            onTabClick = onTabClick,
            isStaleData = isStaleData,
            onDismissStale = viewModel::dismissStaleBanner,
            showReadinessBanner = showReadinessBanner,
            callReadiness = callReadiness,
            onRetrySip = viewModel::retrySipRegistration,
            navController = navController,
            onClientSelected = onClientSelected,
            clientsScrollToTopTick = clientsScrollToTopTick,
            agendaScrollToTopTick = agendaScrollToTopTick,
            onLogout = onLogout,
        )
        return
    }

    // Wide layout: collapsed NavigationRail on the left, content on
    // the right. No expand/collapse toggle — see note above.
    //
    // Background painted explicitly: ListDetailPaneScaffold leaves the
    // gutter between panes transparent, and gesture-nav devices have
    // a strip below our content where the system would otherwise show
    // its window background (white on AOSP, even in dark theme).
    // Painting the Row with the theme background covers both spots.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Status bar + cutout padding for the whole shell. Bottom
            // is handled inside the content column so the rail can
            // run floor-to-ceiling on the left edge.
            //
            // consumeWindowInsets is critical: PreCallScreen's Hero
            // applies its own statusBars padding internally (a leftover
            // from running full-screen). Without consuming the inset
            // here, the Hero would re-apply it on top of ours →
            // double-margin at the top of the detail pane.
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .consumeWindowInsets(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        CenteredNavigationRail(
            tabs = tabs,
            selectedRoute = selectedRoute,
            onTabClick = onTabClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Bottom inset for content only — the rail/drawer
                // visually runs to the edge, but content respects
                // the system nav bar. Consume after applying so that
                // PreCallScreen's internal CallActionBar doesn't try
                // to re-pad against the system nav.
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
                )
                .consumeWindowInsets(
                    WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
                ),
        ) {
            AnimatedVisibility(visible = isStaleData) {
                StaleDataBanner(onDismiss = viewModel::dismissStaleBanner)
            }
            AnimatedVisibility(visible = showReadinessBanner) {
                CallReadinessBanner(
                    state = callReadiness,
                    onRetry = viewModel::retrySipRegistration,
                )
            }
            InnerNavHost(
                navController = navController,
                onClientSelected = onClientSelected,
                clientsScrollToTopTick = clientsScrollToTopTick,
                agendaScrollToTopTick = agendaScrollToTopTick,
                onLogout = onLogout,
            )
        }
    }
}

/**
 * Compact shell: classic Scaffold + NavigationBar. Preserved for
 * phone-form-factor emulator runs and previews; production agents
 * never see this branch.
 */
@Composable
private fun CompactScaffold(
    tabs: List<TabItem>,
    selectedRoute: (String) -> Boolean,
    onTabClick: (String) -> Unit,
    isStaleData: Boolean,
    onDismissStale: () -> Unit,
    showReadinessBanner: Boolean,
    callReadiness: CallReadiness,
    onRetrySip: () -> Unit,
    navController: NavController,
    onClientSelected: (String) -> Unit,
    clientsScrollToTopTick: Int,
    agendaScrollToTopTick: Int,
    onLogout: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val tabLabel = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = selectedRoute(tab.route),
                        onClick = { onTabClick(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tabLabel) },
                        label = { Text(tabLabel) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = isStaleData) {
                StaleDataBanner(onDismiss = onDismissStale)
            }
            AnimatedVisibility(visible = showReadinessBanner) {
                CallReadinessBanner(state = callReadiness, onRetry = onRetrySip)
            }
            InnerNavHost(
                navController = navController,
                onClientSelected = onClientSelected,
                clientsScrollToTopTick = clientsScrollToTopTick,
                agendaScrollToTopTick = agendaScrollToTopTick,
                onLogout = onLogout,
            )
        }
    }
}

/**
 * Vertical rail with items centered vertically. No hamburger header
 * — the rail is permanently collapsed (no drawer-expanded mode).
 * Crediiz agents work primarily inside the content panes; a
 * toggleable drawer was eating horizontal space that the list/detail
 * scaffold needs more.
 */
@Composable
private fun CenteredNavigationRail(
    tabs: List<TabItem>,
    selectedRoute: (String) -> Boolean,
    onTabClick: (String) -> Unit,
) {
    NavigationRail {
        // Weighted spacers above and below the items center them
        // vertically in the rail's full height.
        Spacer(Modifier.weight(1f))
        tabs.forEach { tab ->
            val tabLabel = stringResource(tab.labelRes)
            NavigationRailItem(
                selected = selectedRoute(tab.route),
                onClick = { onTabClick(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tabLabel) },
                label = { Text(tabLabel) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun InnerNavHost(
    navController: NavController,
    onClientSelected: (String) -> Unit,
    clientsScrollToTopTick: Int,
    agendaScrollToTopTick: Int,
    onLogout: () -> Unit,
) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = HomeTabs.CLIENTS,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(HomeTabs.CLIENTS) {
            ClientsScreen(
                onClientSelected = onClientSelected,
                onStartAutoCall = { firstClientId ->
                    onClientSelected(firstClientId)
                },
                scrollToTopTick = clientsScrollToTopTick,
            )
        }
        composable(HomeTabs.AGENDA) {
            AgendaScreen(
                onFollowUpSelected = onClientSelected,
                scrollToTopTick = agendaScrollToTopTick,
            )
        }
        composable(HomeTabs.SETTINGS) {
            SettingsScreen(onLoggedOut = onLogout)
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
                    text = stringResource(R.string.home_stale_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_stale_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_dismiss),
                )
            }
        }
    }
}
