package com.project.vortex.callsagent.presentation.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.presentation.common.ClientDetailPane
import com.project.vortex.callsagent.presentation.common.WindowSize
import kotlinx.coroutines.launch

/**
 * Public Agenda-tab entry point. Mirrors
 * [com.project.vortex.callsagent.presentation.clients.ClientsScreen]:
 * - **Compact**: plain list pane.
 * - **Wide**: list/detail with free-drag divider (anchors only at
 *   extremes). Tapping a follow-up while the divider sits at
 *   list-full flips to detail-full, matching the Gmail behavior.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AgendaScreen(
    onFollowUpSelected: (String) -> Unit,
    scrollToTopTick: Int = 0,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    if (!WindowSize.isWideWidth) {
        AgendaListPane(
            onFollowUpSelected = onFollowUpSelected,
            scrollToTopTick = scrollToTopTick,
            viewModel = viewModel,
        )
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    // Three-zone anchor layout: snap-magnetic edges (0%–20% and
    // 80%–100%) plus free-drag center (20%–80% in 5% steps). See
    // ClientsScreenAdaptive for the full rationale.
    val anchors = remember {
        buildList {
            add(PaneExpansionAnchor.Proportion(0f))
            // Free-drag zone: 30%..70% in 5% increments — see
            // ClientsScreenAdaptive for the full rationale.
            for (step in 6..14) {
                add(PaneExpansionAnchor.Proportion(step / 20f))
            }
            add(PaneExpansionAnchor.Proportion(1f))
        }
    }
    val detailFullAnchor = anchors.first()
    val listFullAnchor = anchors.last()
    val paneExpansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = anchors,
    )

    val selectedClientId = navigator.currentDestination?.contentKey

    // Back-arrow visibility — true when divider is at detail-full OR
    // when the scaffold is in single-pane mode (list hidden by
    // adaptive sizing despite passing `isWideWidth`). See
    // ClientsScreenAdaptive for full rationale.
    val isDetailFull = paneExpansionState.currentAnchor == detailFullAnchor
    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] !=
        PaneAdaptedValue.Expanded
    val showBack = isDetailFull || listHidden

    BackHandler(enabled = showBack) {
        scope.launch {
            if (listHidden) {
                navigator.navigateBack()
            } else {
                paneExpansionState.animateTo(listFullAnchor)
            }
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                AgendaListPane(
                    onFollowUpSelected = { clientId ->
                        scope.launch {
                            if (paneExpansionState.currentAnchor == listFullAnchor) {
                                paneExpansionState.animateTo(detailFullAnchor)
                            }
                            navigator.navigateTo(
                                pane = ListDetailPaneScaffoldRole.Detail,
                                contentKey = clientId,
                            )
                        }
                    },
                    scrollToTopTick = scrollToTopTick,
                    viewModel = viewModel,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                // See ClientsScreenAdaptive for the rationale on each
                // callback's remapping to pane-state actions.
                ClientDetailPane(
                    clientId = selectedClientId,
                    showBackArrow = showBack,
                    onBack = {
                        scope.launch {
                            if (listHidden) {
                                navigator.navigateBack()
                            } else {
                                paneExpansionState.animateTo(listFullAnchor)
                            }
                        }
                    },
                    onSkipToNext = { nextClientId ->
                        scope.launch {
                            navigator.navigateTo(
                                pane = ListDetailPaneScaffoldRole.Detail,
                                contentKey = nextClientId,
                            )
                        }
                    },
                    onSkipToSummary = { /* no-op in pane mode */ },
                    onExitAutoCall = {
                        scope.launch {
                            paneExpansionState.animateTo(listFullAnchor)
                        }
                    },
                )
            }
        },
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = { state ->
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(
                    state = state,
                    minTouchTargetSize = 48.dp,
                    interactionSource = interactionSource,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}
