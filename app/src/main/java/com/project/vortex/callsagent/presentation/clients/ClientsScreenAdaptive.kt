package com.project.vortex.callsagent.presentation.clients

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.presentation.common.ClientDetailPane
import com.project.vortex.callsagent.presentation.common.WindowSize
import kotlinx.coroutines.launch

/**
 * Public Clients-tab entry point.
 *
 * - **Compact**: plain list pane; tap navigates to PreCall full-screen.
 * - **Wide**: `NavigableListDetailPaneScaffold` with a free-drag
 *   divider (anchors only at 0% / 100% — the user can park the split
 *   anywhere in between, with snap only at the extremes, mirroring
 *   Gmail's behavior). When the divider is at the list-only extreme
 *   (detail collapsed), selecting a client animates the divider to
 *   detail-only so the agent flips fully to the detail view — same as
 *   tapping a thread in Gmail when the list is full-width.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ClientsScreen(
    onClientSelected: (String) -> Unit,
    onStartAutoCall: (firstClientId: String) -> Unit,
    scrollToTopTick: Int = 0,
    viewModel: ClientsViewModel = hiltViewModel(),
) {
    if (!WindowSize.isWideWidth) {
        ClientsListPane(
            onClientSelected = onClientSelected,
            onStartAutoCall = onStartAutoCall,
            scrollToTopTick = scrollToTopTick,
            viewModel = viewModel,
        )
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    // Three-zone anchor layout — same shape as Gmail / iOS Mail:
    //
    //   0% ━━━━━━━ 30% ━ 35% ━ ... ━ 65% ━ 70% ━━━━━━━ 100%
    //   └─ snap ──┘└─── free drag (5% step) ───┘└─── snap ──┘
    //
    // No anchors live between 0%↔30% or 70%↔100%. With snap-to-nearest
    // semantics, a release inside either dead zone falls into 0%/30%
    // or 70%/100% depending on which side of the midpoint it sat on:
    //
    //   release at 14% → 0%        release at 86% → 100%
    //   release at 16% → 30%       release at 84% → 70%
    //
    // Between 30% and 70% we keep 5% steps so the divider has the
    // sensation of free drag (max 2.5% snap distance, imperceptible).
    val anchors = remember {
        buildList {
            add(PaneExpansionAnchor.Proportion(0f))
            // Free-drag zone: 30%..70% in 5% increments (9 anchors).
            for (step in 6..14) {
                add(PaneExpansionAnchor.Proportion(step / 20f))
            }
            add(PaneExpansionAnchor.Proportion(1f))
        }
    }
    val detailFullAnchor = anchors.first() // 0% = detail takes everything
    val listFullAnchor = anchors.last()    // 100% = list takes everything
    val paneExpansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = anchors,
    )

    val selectedClientId = navigator.currentDestination?.contentKey

    // ── Auto-call session ↔ detail pane sync ──────────────────────────
    // When PostCall save advances the AutoCallOrchestrator to the
    // next client, the AppNavGraph pops back to Home (split mode)
    // and this effect repoints the detail pane to that next client.
    //
    // **What this effect does NOT do:** force-navigate when the
    // session FIRST starts. If the agent already had a client open
    // in the detail pane and tapped Auto-call, we keep them on that
    // client — the FAB just arms the session over the queue without
    // overriding what the agent was looking at. The `sessionWasActive`
    // latch tracks whether we've seen the session running before; the
    // first transition `null → active` registers `lastObservedSessionClient`
    // silently (no navigation). Subsequent currentIndex advances
    // navigate the pane as before.
    val autoCallSession by viewModel.autoCallSession.collectAsState()
    val sessionCurrentClient = autoCallSession?.let { s ->
        s.queue.getOrNull(s.currentIndex)
    }
    // `rememberSaveable` is critical here. The PostCall route covers
    // HomeScreen full-screen, which DISPOSES HomeScreen's composition
    // (NavHost default). When PostCall pops and HomeScreen returns,
    // a plain `remember` would reset both latches to their defaults
    // (`null` and `false`), causing the LaunchedEffect to re-enter
    // the "Session just started" branch and silently swallow the
    // post-save advance — the agent would never see the next client.
    // `rememberSaveable` persists the values via SavedStateHandle so
    // the latches survive across the PostCall → HomeScreen transition.
    var lastObservedSessionClient by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionWasActive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionCurrentClient, autoCallSession != null) {
        val isActive = autoCallSession != null
        when {
            // Session just ended: clear latches so the next session
            // can fire its own initial-state recording correctly.
            !isActive && sessionWasActive -> {
                sessionWasActive = false
                lastObservedSessionClient = null
            }
            // Session just started: register the initial cursor WITHOUT
            // navigating. The FAB onClick already decided where to put
            // the user — we don't override that decision.
            isActive && !sessionWasActive -> {
                sessionWasActive = true
                lastObservedSessionClient = sessionCurrentClient
            }
            // Already in session, and the cursor moved to a different
            // client → this is a real advance (PostCall save +1, skip,
            // etc.). Navigate the pane to follow.
            isActive && sessionWasActive &&
                sessionCurrentClient != null &&
                sessionCurrentClient != lastObservedSessionClient -> {
                lastObservedSessionClient = sessionCurrentClient
                if (paneExpansionState.currentAnchor == listFullAnchor) {
                    paneExpansionState.animateTo(detailFullAnchor)
                }
                navigator.navigateTo(
                    pane = ListDetailPaneScaffoldRole.Detail,
                    contentKey = sessionCurrentClient,
                )
            }
        }
    }

    // Back-arrow visibility — true in two cases:
    //  1. Divider pinned at the detail-only end (user dragged the
    //     handle to 0%): list is technically rendered but at 0dp.
    //  2. Scaffold is in SINGLE-PANE mode (screen too narrow for
    //     dual-pane, despite passing the `isWideWidth` gate via the
    //     navigation rail — common on Tab A9 portrait). In that mode
    //     `currentAnchor` doesn't track visibility, so we ask the
    //     scaffold directly via `scaffoldValue` whether the list is
    //     currently hidden.
    val isDetailFull = paneExpansionState.currentAnchor == detailFullAnchor
    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] !=
        PaneAdaptedValue.Expanded
    val showBack = isDetailFull || listHidden

    // Mirror the back-arrow behavior onto the system back gesture/key
    // for both cases.
    BackHandler(enabled = showBack) {
        scope.launch {
            if (listHidden) {
                // Single-pane mode: nav back to list destination.
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
                ClientsListPane(
                    onClientSelected = { clientId ->
                        scope.launch {
                            // If the divider is currently pinned at
                            // list-only (Gmail "inbox full-width"
                            // mode), animate it to detail-only so
                            // selecting a client flips the view fully
                            // to the detail pane. Otherwise the new
                            // selection would update the (hidden)
                            // detail pane with no visible change.
                            if (paneExpansionState.currentAnchor == listFullAnchor) {
                                paneExpansionState.animateTo(detailFullAnchor)
                            }
                            navigator.navigateTo(
                                pane = ListDetailPaneScaffoldRole.Detail,
                                contentKey = clientId,
                            )
                        }
                    },
                    onStartAutoCall = { firstClientId ->
                        // The auto-call session was already activated
                        // by the FAB's `viewModel.startAutoCall()`.
                        // This callback decides what to *display*.
                        //
                        // Rule: respect the agent's current selection.
                        //  - If a client is already shown in the detail
                        //    pane, KEEP it. The agent picked that
                        //    client intentionally; the session should
                        //    activate over the queue but not yank the
                        //    user out of where they were looking.
                        //  - If nothing is shown yet (e.g. the agent
                        //    tapped Auto-call straight from a clean
                        //    list view), open the queue's first
                        //    client as a sensible default.
                        //
                        // After the call finishes and PostCall save
                        // advances the orchestrator past `clientId`
                        // (handled by the queue-position fix in
                        // `onPostCallSaved`), the LaunchedEffect above
                        // observes the cursor change and swaps the
                        // pane to the right next client.
                        if (selectedClientId == null) {
                            scope.launch {
                                if (paneExpansionState.currentAnchor == listFullAnchor) {
                                    paneExpansionState.animateTo(detailFullAnchor)
                                }
                                navigator.navigateTo(
                                    pane = ListDetailPaneScaffoldRole.Detail,
                                    contentKey = firstClientId,
                                )
                            }
                        }
                    },
                    scrollToTopTick = scrollToTopTick,
                    viewModel = viewModel,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                // Detail pane hosts the FULL PreCallScreen — call CTA,
                // notes, sheets, auto-call countdown, everything. The
                // navigation-flavored callbacks below are remapped to
                // act on the pane state, not on Nav-Compose stack:
                //   - back → animate divider to list-full.
                //   - skip-to-next → swap the pane's contentKey.
                //   - exit-auto-call → clear selection + show list.
                //   - skip-to-summary → no-op in pane mode (auto-call
                //     queue exhaustion while inside the pane is not a
                //     supported flow — agents run auto-call from the
                //     full-screen route via the FAB).
                ClientDetailPane(
                    clientId = selectedClientId,
                    showBackArrow = showBack,
                    onBack = {
                        scope.launch {
                            if (listHidden) {
                                // Single-pane mode: pop the detail
                                // destination via the navigator so the
                                // scaffold returns to the list pane.
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
                    onSkipToSummary = { /* no-op — see comment above */ },
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
