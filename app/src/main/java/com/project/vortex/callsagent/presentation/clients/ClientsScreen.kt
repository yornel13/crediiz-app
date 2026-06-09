package com.project.vortex.callsagent.presentation.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.common.enums.MissedCallReason
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.MissedCall
import com.project.vortex.callsagent.presentation.clients.components.ClientsViewSelector
import com.project.vortex.callsagent.presentation.clients.components.DismissClientSheet
import com.project.vortex.callsagent.presentation.clients.components.RecentClientCard
import com.project.vortex.callsagent.presentation.clients.components.RecentStatusChangeCard
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.Amber600
import com.project.vortex.callsagent.ui.theme.Emerald600
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.Rose600
import com.project.vortex.callsagent.presentation.common.relativePast
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Internal list-pane composable for the Clients tab.
 *
 * Originally this was `ClientsScreen`. Renamed when the screen was
 * split into adaptive list/detail panes for tablets — the public
 * entry point now lives in [ClientsScreenAdaptive] (file
 * `ClientsScreenAdaptive.kt`), which picks between this list pane on
 * its own (compact widths) or list+detail with a draggable divider
 * (wide widths).
 *
 * Keeping this composable internal-but-unchanged minimizes risk:
 * the entire pre-existing UI behavior (search, view kinds, FAB,
 * dismissal sheet, missed-calls sheet, scroll-to-top tick) is
 * preserved bit-for-bit on phones, and reused as the list pane on
 * tablets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientsListPane(
    onClientSelected: (String) -> Unit,
    onStartAutoCall: (firstClientId: String) -> Unit,
    /**
     * Increments every time the agent re-taps the Clients tab on the
     * bottom nav while already on it. Each new value triggers a
     * scroll-to-top of the list. Default 0 — no scroll on first
     * composition.
     */
    scrollToTopTick: Int = 0,
    viewModel: ClientsViewModel = hiltViewModel(),
) {
    val pendingNeverCalled by viewModel.pendingNeverCalled.collectAsState()
    val pendingNeverCalledByDate by viewModel.pendingNeverCalledByDate.collectAsState()
    val pendingForRetry by viewModel.pendingForRetry.collectAsState()
    val recentEntries by viewModel.recentEntries.collectAsState()
    val totalPending by viewModel.totalPendingCount.collectAsState()
    val totalRecent by viewModel.totalRecentCount.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val viewKind by viewModel.viewKind.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val missedCalls by viewModel.missedCalls.collectAsState()
    val syncIndicator by viewModel.syncIndicator.collectAsState()
    val autoCallSession by viewModel.autoCallSession.collectAsState()
    val isAutoCallActive = autoCallSession != null
    var missedSheetOpen by remember { mutableStateOf(false) }
    // (clientId, clientName) currently being dismissed via the sheet.
    var dismissTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val pendingListSize = pendingNeverCalled.size + pendingForRetry.size
    val activeListSize = when (viewKind) {
        ClientsViewKind.PENDIENTES -> pendingListSize
        ClientsViewKind.RECIENTES -> recentEntries.size
    }

    // Tap-to-scroll-top — see HomeScreen `clientsScrollToTopTick`.
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

    // Gmail-style FAB expand/collapse — show the label text only when
    // the list is at the very top; collapse to an icon-only pill on
    // any scroll. `derivedStateOf` keeps the recomposition window
    // tight: we only re-evaluate the boolean when the underlying
    // scroll position properties actually change, not on every
    // millisecond of scroll.
    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }

    Scaffold(
        floatingActionButton = {
            // FAB visibility rule:
            //  - Auto-call ACTIVE → show always (any tab) so the agent
            //    can deactivate from anywhere.
            //  - Auto-call IDLE  → show only on Pendientes with a
            //    non-empty queue. The button has no meaning on
            //    Recientes or with an empty pendientes list.
            val showFab = isAutoCallActive ||
                (pendingListSize > 0 && viewKind == ClientsViewKind.PENDIENTES)

            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isAutoCallActive) {
                            // Toggle off: cancel the session. The FAB
                            // re-arms automatically as soon as the
                            // orchestrator clears `session`.
                            viewModel.exitAutoCall()
                        } else {
                            val firstClientId = viewModel.startAutoCall()
                            if (firstClientId != null) onStartAutoCall(firstClientId)
                        }
                    },
                    // Gmail-style: expanded at the top of the list,
                    // collapsed to icon-only as soon as the user
                    // scrolls. M3 animates the width transition.
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            // Close glyph signals "tap to stop" when a
                            // session is running; phone-in-talk signals
                            // "tap to start" when idle.
                            imageVector = if (isAutoCallActive) {
                                Icons.Filled.Close
                            } else {
                                Icons.Filled.PhoneInTalk
                            },
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(
                            text = if (isAutoCallActive) {
                                stringResource(R.string.clients_fab_deactivate)
                            } else {
                                stringResource(R.string.clients_fab_auto_call)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    containerColor = PhoneGreen,
                    // Dark content over the saturated PhoneGreen —
                    // matches the LLAMAR button styling in PreCall's
                    // bottom bar for visual consistency.
                    contentColor = Color.Black,
                )
            }
        },
        // HomeScreen's outer Scaffold already consumes the system insets;
        // skipping them here avoids double-padding the top/bottom of the screen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("hero") {
                Hero(
                    activeView = viewKind,
                    pendingCount = totalPending,
                    recentCount = totalRecent,
                    syncState = syncIndicator,
                    onForceSync = viewModel::forceSyncNow,
                )
            }

            if (missedCalls.isNotEmpty()) {
                item("missed_banner") {
                    MissedCallsBanner(
                        count = missedCalls.size,
                        onClick = { missedSheetOpen = true },
                    )
                }
            }

            item("search") {
                SearchField(query = query, onQueryChange = viewModel::onSearchQueryChange)
            }

            item("view_selector") {
                ClientsViewSelector(
                    selected = viewKind,
                    pendingCount = totalPending,
                    recentCount = totalRecent,
                    onSelected = viewModel::onViewKindChange,
                )
            }

            // Show "X of Y" hint only while a search is active. The
            // total-of count maps to the active view to keep the math
            // honest.
            if (query.isNotBlank()) {
                item("search_summary") {
                    SearchResultSummary(
                        matchCount = activeListSize,
                        totalCount = when (viewKind) {
                            ClientsViewKind.PENDIENTES -> totalPending
                            ClientsViewKind.RECIENTES -> totalRecent
                        },
                    )
                }
            }

            uiState.errorMessage?.let { msg ->
                item("error") { ErrorBanner(message = msg) }
            }

            if (activeListSize == 0) {
                item("empty") {
                    EmptyState(
                        viewKind = viewKind,
                        isLoading = uiState.isRefreshing,
                        isSearching = query.isNotBlank(),
                    )
                }
            } else when (viewKind) {
                ClientsViewKind.PENDIENTES -> {
                    if (pendingNeverCalled.isNotEmpty()) {
                        item("pending_subheader_never") {
                            PendingSubHeader(
                                label = stringResource(R.string.clients_subheader_untouched),
                                count = pendingNeverCalled.size,
                            )
                        }
                        // While searching, headers are noise — the
                        // agent is already navigating by name/phone.
                        // Render a flat filtered list in that case.
                        if (query.isNotBlank()) {
                            items(pendingNeverCalled, key = { "pn_${it.id}" }) { client ->
                                ClientCard(
                                    client = client,
                                    onClick = { onClientSelected(client.id) },
                                    onDismiss = { dismissTarget = client.id to client.name },
                                )
                            }
                        } else {
                            // Group by assignedAt date, oldest first.
                            // LinkedHashMap iteration order = sort order
                            // (see groupPendingNeverCalledByAssignedDate).
                            pendingNeverCalledByDate.forEach { (bucket, clients) ->
                                item("pn_h_${bucket.key}") {
                                    PendingDateHeader(bucket = bucket)
                                }
                                items(clients, key = { "pn_${it.id}" }) { client ->
                                    ClientCard(
                                        client = client,
                                        onClick = { onClientSelected(client.id) },
                                        onDismiss = {
                                            dismissTarget = client.id to client.name
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (pendingForRetry.isNotEmpty()) {
                        item("pending_subheader_retry") {
                            PendingSubHeader(
                                label = stringResource(R.string.clients_subheader_retry),
                                count = pendingForRetry.size,
                                hint = stringResource(R.string.clients_subheader_retry_hint),
                            )
                        }
                        items(pendingForRetry, key = { "pr_${it.id}" }) { client ->
                            ClientCard(
                                client = client,
                                onClick = { onClientSelected(client.id) },
                                onDismiss = { dismissTarget = client.id to client.name },
                            )
                        }
                    }
                }
                ClientsViewKind.RECIENTES -> {
                    items(recentEntries, key = { it.sortKey }) { entry ->
                        when (entry) {
                            is RecentEntry.Called -> RecentClientCard(
                                client = entry.client,
                                onOpen = { onClientSelected(entry.client.id) },
                            )
                            is RecentEntry.StatusChanged -> RecentStatusChangeCard(
                                client = entry.client,
                                change = entry.change,
                                onOpen = { onClientSelected(entry.client.id) },
                            )
                        }
                    }
                }
            }
        }

        dismissTarget?.let { (id, name) ->
            DismissClientSheet(
                clientName = name,
                onDismiss = { dismissTarget = null },
                onConfirm = { code, free ->
                    viewModel.dismissClient(id, code, free)
                    dismissTarget = null
                },
            )
        }

        if (missedSheetOpen) {
            MissedCallsSheet(
                missedCalls = missedCalls,
                onDismiss = { missedSheetOpen = false },
                onCallBack = { missed ->
                    val matched = missed.matchedClientId
                    if (matched != null) {
                        onClientSelected(matched)
                        viewModel.acknowledgeMissedCall(missed.id)
                        missedSheetOpen = false
                    } else {
                        // Number not in queue — acknowledge so it stops nagging.
                        // A future enhancement could prompt to dial anyway.
                        viewModel.acknowledgeMissedCall(missed.id)
                    }
                },
                onDismissEntry = viewModel::acknowledgeMissedCall,
                onDismissAll = {
                    viewModel.acknowledgeAllMissedCalls()
                    missedSheetOpen = false
                },
            )
        }
    }
}

@Composable
private fun Hero(
    activeView: ClientsViewKind,
    pendingCount: Int,
    recentCount: Int,
    syncState: SyncIndicatorState,
    onForceSync: () -> Unit,
) {
    val (headlineCount, headlineUnit) = when (activeView) {
        ClientsViewKind.PENDIENTES ->
            pendingCount to pluralStringResource(
                R.plurals.clients_headline_to_call,
                pendingCount,
            )
        ClientsViewKind.RECIENTES ->
            recentCount to pluralStringResource(
                R.plurals.clients_headline_recent_call,
                recentCount,
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.clients_hero_your_queue),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            SyncChip(state = syncState, onClick = onForceSync)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = headlineCount.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = headlineUnit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * Compact sync-status chip in the top-right of the Clients hero. Color
 * + label come from [SyncIndicatorState]. Tapping triggers an immediate
 * sync (covers the offline-then-online case where the agent doesn't
 * want to wait for the periodic worker).
 */
@Composable
private fun SyncChip(state: SyncIndicatorState, onClick: () -> Unit) {
    val (icon, label, color) = when (state) {
        SyncIndicatorState.Syncing ->
            Triple(
                Icons.Filled.Sync,
                stringResource(R.string.clients_sync_syncing),
                MaterialTheme.colorScheme.primary,
            )
        SyncIndicatorState.AllSynced ->
            Triple(Icons.Filled.CheckCircle, stringResource(R.string.clients_sync_synced), Emerald600)
        is SyncIndicatorState.Pending ->
            Triple(
                Icons.Filled.Sync,
                stringResource(R.string.clients_sync_pending, state.count),
                Amber600,
            )
        is SyncIndicatorState.Failed ->
            Triple(
                Icons.Filled.CloudOff,
                stringResource(R.string.clients_sync_unsynced, state.pendingCount),
                Rose600,
            )
    }

    val container = color.copy(alpha = 0.12f)

    Surface(
        shape = PillShape,
        color = container,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Two-state search input.
 *
 * - **Idle**: a clickable Surface that looks like a search field but is
 *   NOT a TextField. There's no input to focus on entry, which means
 *   the system can't auto-open the IME just because the composition
 *   contains a focusable input. This is the Gmail-Android pattern.
 * - **Active**: an actual [TextField] requesting focus on mount. The
 *   IME opens because the agent expressly tapped to search — not as a
 *   side-effect of layout. When the agent dismisses focus AND the
 *   query is blank, we revert to Idle so the input is gone again.
 *
 * If the agent navigates away with a non-blank query and returns, we
 * start in Active so the cursor sits in the existing text where the
 * agent left it.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    // Persist editing mode across recompositions but reset on screen
    // re-entry. Initial state = Active iff there's already a query
    // (returning to a partially-typed search).
    var isActive by rememberSaveable(query.isNotBlank()) { mutableStateOf(query.isNotBlank()) }
    // Latch that flips on the FIRST time the TextField is focused.
    // Without it, the initial onFocusChanged callback (which fires with
    // isFocused=false on mount, BEFORE our requestFocus has run) would
    // collapse the field straight back to Idle on the first frame —
    // since query is also blank at that point. Only after we've had
    // focus once do we trust an unfocused+blank state as "user dismissed".
    var hasReceivedFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (!isActive) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isActive = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.clients_search_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        return
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.clients_search_placeholder)) },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hasReceivedFocus = true
                    return@onFocusChanged
                }
                // Only collapse to Idle once we've had focus at least
                // once — otherwise the initial unfocused callback on
                // mount (which fires before requestFocus completes)
                // would close the field on the first frame.
                if (hasReceivedFocus && query.isBlank()) {
                    isActive = false
                }
            },
    )

    // Request focus + show the IME exactly once when we enter Active
    // mode. This is the only place in the screen where focus is asked
    // programmatically, and it only happens after a user-initiated tap
    // on the placeholder.
    //
    // awaitFrame() is critical: FocusRequester.requestFocus() silently
    // fails when the focusable Modifier hasn't completed its layout
    // pass yet. The composition→layout gap is sub-frame, but races
    // exist on slower devices. Waiting one frame guarantees the
    // TextField is positioned before we touch its focus.
    //
    // keyboardController.show() is a belt-and-suspenders call: focus
    // alone usually opens the IME, but on some OEMs/IMEs the implicit
    // show after focus is suppressed if another window had focus last
    // (back-to-back navigations, dialogs, etc.). Calling show()
    // explicitly removes that ambiguity.
    LaunchedEffect(Unit) {
        awaitFrame()
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClientCard(
    client: Client,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Long-press replaces the kebab (3-dots) button — gains us the
    // horizontal space the icon used to occupy and removes constant
    // visual noise from the card. Discoverability is acceptable for
    // an in-house tool used 8h/day; agents pick it up on day one.
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name = client.name, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = client.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Long-press context menu — anchored to the card.
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clients_menu_dismiss_client)) },
                        onClick = {
                            menuOpen = false
                            onDismiss()
                        },
                    )
                }
            }

            // Meta row — attempts + last call + status pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AttemptsChip(attempts = client.callAttempts)
                    if (client.lastCalledAt != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· ${formatRelative(client.lastCalledAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // If the client already has a recorded last outcome
                // (NO_ANSWER / BUSY for the retry sub-section), surface
                // it on the pill — it carries more information than the
                // generic "Pending" status.
                val outcome = client.lastOutcome
                if (outcome != null) {
                    StatusPill(
                        label = outcome.label(),
                        palette = outcome.palette(),
                    )
                } else {
                    StatusPill(
                        label = client.status.label(),
                        palette = client.status.palette(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingSubHeader(
    label: String,
    count: Int,
    hint: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Section header that splits the "Sin llamar" sub-feed by assignedAt
 * date (Hoy / Ayer / Antier / weekday / "Más antiguos"). Visually
 * lighter than [PendingSubHeader] — it's a sub-grouping inside the
 * Untouched section, not a top-level pill.
 */
@Composable
private fun PendingDateHeader(bucket: PendingDateBucket) {
    Text(
        text = bucket.resolveLabel(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun AttemptsChip(attempts: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (attempts == 0) {
                stringResource(R.string.clients_attempts_none)
            } else {
                pluralStringResource(R.plurals.clients_attempts, attempts, attempts)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultSummary(matchCount: Int, totalCount: Int) {
    Text(
        text = stringResource(R.string.clients_search_summary, matchCount, totalCount),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EmptyState(
    viewKind: ClientsViewKind,
    isLoading: Boolean,
    isSearching: Boolean,
) {
    val (title, subtitle, icon) = when {
        isLoading -> Triple(
            stringResource(R.string.clients_empty_loading_title),
            stringResource(R.string.clients_empty_loading_subtitle),
            Icons.Filled.Phone,
        )
        isSearching -> Triple(
            stringResource(R.string.clients_empty_search_title),
            stringResource(R.string.clients_empty_search_subtitle),
            Icons.Filled.Search,
        )
        else -> when (viewKind) {
            ClientsViewKind.PENDIENTES -> Triple(
                stringResource(R.string.clients_empty_pending_title),
                stringResource(R.string.clients_empty_pending_subtitle),
                Icons.Filled.Phone,
            )
            ClientsViewKind.RECIENTES -> Triple(
                stringResource(R.string.clients_empty_recent_title),
                stringResource(R.string.clients_empty_recent_subtitle),
                Icons.Filled.Phone,
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Missed-call banner + sheet (Phase 3.5) ─────────────────────────────

@Composable
private fun MissedCallsBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PhoneMissed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.clients_missed_banner, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.clients_missed_banner_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissedCallsSheet(
    missedCalls: List<MissedCall>,
    onDismiss: () -> Unit,
    onCallBack: (MissedCall) -> Unit,
    onDismissEntry: (id: String) -> Unit,
    onDismissAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.clients_missed_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (missedCalls.size > 1) {
                    TextButton(onClick = onDismissAll) {
                        Text(stringResource(R.string.clients_missed_dismiss_all))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.clients_missed_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            missedCalls.forEach { missed ->
                MissedCallRow(
                    missed = missed,
                    onCallBack = { onCallBack(missed) },
                    onDismiss = { onDismissEntry(missed.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MissedCallRow(
    missed: MissedCall,
    onCallBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = missed.matchedClientId?.let {
        stringResource(R.string.clients_missed_assigned_client)
    } ?: stringResource(R.string.clients_missed_unknown_number)
    val canCallBack = missed.matchedClientId != null
    val reasonLabel = when (missed.reason) {
        MissedCallReason.REJECTED -> stringResource(R.string.clients_missed_reason_rejected)
        MissedCallReason.NOT_ANSWERED -> stringResource(R.string.clients_missed_reason_missed)
        MissedCallReason.BUSY_OTHER_CALL -> stringResource(R.string.clients_missed_reason_busy)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = missed.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = reasonLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatRelative(missed.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canCallBack) {
                OutlinedButton(
                    onClick = onCallBack,
                    shape = PillShape,
                ) {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.clients_missed_call_back),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    // Render against the business clock so "yesterday" / "this week"
    // boundaries match what admin in Panama sees. See BusinessConfig.
    val zone = BusinessConfig.BUSINESS_TIMEZONE
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    return if (minutes < 60 * 24 * 7) {
        relativePast(instant, now)
    } else {
        // Older than a week → absolute "MMM d" in the current locale.
        java.time.format.DateTimeFormatter
            .ofPattern("MMM d", java.util.Locale.getDefault())
            .format(instant.atZone(zone))
    }
}
