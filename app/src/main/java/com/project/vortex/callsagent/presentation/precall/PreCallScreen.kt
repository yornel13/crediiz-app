package com.project.vortex.callsagent.presentation.precall

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.domain.model.ActivityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.domain.call.CallReadiness
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.presentation.autocall.AutoCallSession
import com.project.vortex.callsagent.presentation.clients.components.DismissClientSheet
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.presentation.autocall.PendingAutoCall
import com.project.vortex.callsagent.presentation.precall.components.AgentStatusChangeSheet
import com.project.vortex.callsagent.presentation.precall.components.ScheduleFollowUpSheet
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.components.CallReadinessBanner
import com.project.vortex.callsagent.ui.components.SectionHeader
import com.project.vortex.callsagent.ui.components.StatusPill
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.StatusPalette
import com.project.vortex.callsagent.ui.theme.Teal700
import com.project.vortex.callsagent.ui.theme.Teal900
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val HeroShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

/**
 * Hard-coded brighter emerald used for the QuickNoteInline dashed
 * border. NOT a theme token — the colour needs to be brighter than
 * the saturated Emerald600 (used for the rail's "you are here" dot
 * and the SALARIO accent) so it reads as a distinct "lime"
 * indicating "input zone", not duplicate semantic green. Lives here
 * (file-local) rather than in `ui/theme/Color.kt` because its only
 * caller is the note input — no reuse expected.
 */
private val NoteInputDashedColor = Color(0xFF34D399)

/**
 * Mutually-exclusive sheet state for the PreCall screen. Replaces a
 * triplet of independent `Boolean` flags that allowed inconsistent
 * combinations (two sheets open at once if callbacks raced).
 *
 * Note: the "Add note" sheet is intentionally NOT here — that one is
 * owned by the ViewModel ([PreCallUiState.isNoteSheetOpen]) because
 * its lifecycle ties to the note-submission progress state. The three
 * sheets here are purely UI-local: they take a selection, hand it
 * back to the VM, and dismiss.
 */
private sealed interface ActiveSheet {
    data object None : ActiveSheet
    data object Dismiss : ActiveSheet
    data object StatusChange : ActiveSheet
    data object Schedule : ActiveSheet
}

// Snackbar text arrives from the ViewModel as a @StringRes at runtime, so it
// must be resolved with context.getString inside the collector (no @Composable
// scope there). The lint rule targets static resource reads, not this case.
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCallScreen(
    clientId: String,
    onBack: (() -> Unit)?,
    onSkipToNext: (clientId: String) -> Unit,
    onSkipToSummary: () -> Unit,
    onExitAutoCall: () -> Unit,
    viewModel: PreCallViewModel = hiltViewModel<PreCallViewModel, PreCallViewModel.Factory>(
        key = clientId,
    ) { factory: PreCallViewModel.Factory -> factory.create(clientId) },
) {
    // Lifecycle-aware collection: pauses when the activity is in
    // background instead of churning CPU/battery on flows the user
    // can't see. Material for an app that stays open 8h/day.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val nextFollowUp by viewModel.nextFollowUp.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val showFullActivityHistory by viewModel.showFullActivityHistory.collectAsStateWithLifecycle()
    val autoCallSession by viewModel.autoCallSession.collectAsStateWithLifecycle()
    val pendingAutoCall by viewModel.pendingAutoCall.collectAsStateWithLifecycle()
    val autoCallDelaySeconds by viewModel.autoCallDelaySeconds.collectAsStateWithLifecycle()
    val callReadiness by viewModel.callReadiness.collectAsStateWithLifecycle()
    val canCall = callReadiness is CallReadiness.Ready

    // Single source of truth for "which sheet is open right now". Three
    // parallel booleans (one per sheet) let invalid combinations slip
    // in — e.g. two sheets open at once if a callback fires before the
    // previous one closed. Sealed-class makes the state exhaustive and
    // mutually exclusive by construction.
    var activeSheet by remember { mutableStateOf<ActiveSheet>(ActiveSheet.None) }

    // repeatOnLifecycle pauses the event collector when the screen is
    // paused — events emitted while we're in background are dropped
    // (they'd race with whatever lands when we resume). For a callback
    // that mutates navigation, this is the safe contract.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is PreCallEvent.SkipToNext -> onSkipToNext(event.clientId)
                    PreCallEvent.SkipToSummary -> onSkipToSummary()
                    PreCallEvent.ExitAutoCall -> onExitAutoCall()
                    // Dismissed = the agent confirmed removal from the queue.
                    // In full-screen mode this pops the stack; in pane mode
                    // (onBack == null) it falls back to onExitAutoCall, which
                    // the caller wires to "animate divider back to list-full"
                    // — same effect (leave the client view).
                    PreCallEvent.Dismissed -> (onBack ?: onExitAutoCall).invoke()
                }
            }
        }
    }

    // Back during an auto-call session also clears the session — keeps the
    // orchestrator state honest (no ghost session badge if the agent
    // navigates back to a non-queue client later).
    //
    // Nullable because the screen runs in two modes:
    //  - Full-screen via Nav-Compose: onBack pops the stack → non-null.
    //  - Embedded as the detail pane: onBack is non-null only when the
    //    divider is at detail-full (so the agent has a way to return to
    //    the list); otherwise null so the Hero's back arrow is hidden
    //    entirely — there's nothing to "go back to" while the list pane
    //    is already visible next to us.
    val effectiveOnBack: (() -> Unit)? = onBack?.let { back ->
        {
            if (autoCallSession != null) viewModel.exitAutoCall()
            else back()
        }
    }

    // Auto-call countdown lives at screen root so the ActionBar stays
    // dumb. Keys must include EVERY input that countdownActive reads,
    // otherwise the effect won't restart when uiState.client loads
    // after pendingAutoCall is already set (race on first composition).
    val pendingClientId = pendingAutoCall?.clientId
    val currentClientId = uiState.client?.id
    val sessionActive = autoCallSession != null
    val countdownActive = pendingClientId != null &&
        pendingClientId == currentClientId &&
        sessionActive &&
        autoCallDelaySeconds > 0
    var countdownRemaining by remember(
        pendingClientId, currentClientId, sessionActive, autoCallDelaySeconds,
    ) {
        mutableIntStateOf(if (countdownActive) autoCallDelaySeconds else 0)
    }
    // Unified countdown driver. Two LaunchedEffects with identical keys
    // is an anti-pattern (both restart together on every key change).
    // This single effect branches between three states:
    //   - no pending auto-call → bail.
    //   - 0 s delay → fire immediately (instant-call path).
    //   - >0 s delay → tick down, then fire.
    LaunchedEffect(pendingClientId, currentClientId, sessionActive, autoCallDelaySeconds) {
        val isPendingForCurrent = pendingClientId != null &&
            pendingClientId == currentClientId &&
            sessionActive
        if (!isPendingForCurrent) return@LaunchedEffect
        if (autoCallDelaySeconds <= 0) {
            viewModel.onAutoCallCountdownComplete()
            return@LaunchedEffect
        }
        val total = autoCallDelaySeconds
        countdownRemaining = total
        for (i in total downTo 1) {
            countdownRemaining = i
            kotlinx.coroutines.delay(1_000)
        }
        viewModel.onAutoCallCountdownComplete()
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Locale-overridden Activity Context — resolves the VM's @StringRes
    // payloads in the language the agent picked in Settings.
    val context = LocalContext.current

    // Forward typed snackbar payloads from the VM to the host. One-shot
    // per emission — duration deliberately Short (the user will be
    // mid-action and a long-lived snackbar is intrusive).
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = context.getString(msg.textRes, *msg.args.toTypedArray()),
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Minimal bottom bar — CTA Call + one secondary action
            // ("Descartar"). Skip/Pausar appear contextually when an
            // auto-call session or countdown is active. Status change
            // moved to the status pill in the header; wrong-number
            // strikes are registered organically via call outcomes.
            CallActionBar(
                client = uiState.client,
                inAutoCall = autoCallSession != null,
                countdownSecondsLeft = if (countdownActive) countdownRemaining else null,
                onCall = viewModel::startCall,
                onSkip = viewModel::skipCurrent,
                onPauseAutoCall = viewModel::cancelAutoCall,
                onDismiss = { activeSheet = ActiveSheet.Dismiss },
                callEnabled = canCall,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier
                .fillMaxSize()
                .padding(padding))

            uiState.client == null -> ErrorState(
                message = uiState.errorMessage
                    ?: stringResource(R.string.precall_client_not_found),
                onBack = effectiveOnBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> PreCallContent(
                client = uiState.client!!,
                activity = activity,
                nextFollowUp = nextFollowUp,
                callReadiness = callReadiness,
                isSubmittingNote = uiState.isSubmittingNote,
                showFullActivityHistory = showFullActivityHistory,
                onRetrySip = viewModel::retrySipRegistration,
                onSaveNote = viewModel::saveManualNote,
                onBack = effectiveOnBack,
                onRequestStatusChange = { activeSheet = ActiveSheet.StatusChange },
                onRequestSchedule = { activeSheet = ActiveSheet.Schedule },
                contentPadding = padding,
            )
        }

        // Exhaustive `when` on the active sheet — moving away from
        // three independent `if (xxxOpen)` branches that could in
        // principle render two sheets at once if state inconsistency
        // ever happened.
        when (activeSheet) {
            ActiveSheet.None -> Unit
            ActiveSheet.StatusChange -> uiState.client?.let { client ->
                AgentStatusChangeSheet(
                    clientName = client.name,
                    currentStatus = client.status,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onConfirm = { change ->
                        viewModel.agentStatusChange(
                            toStatus = change.status,
                            removalReason = change.removalReason,
                            reason = change.reason,
                        )
                        activeSheet = ActiveSheet.None
                    },
                )
            }
            ActiveSheet.Dismiss -> DismissClientSheet(
                clientName = uiState.client?.name.orEmpty(),
                onDismiss = { activeSheet = ActiveSheet.None },
                onConfirm = { removalReason, free ->
                    viewModel.dismissClient(removalReason, free)
                    activeSheet = ActiveSheet.None
                },
            )
            ActiveSheet.Schedule -> uiState.client?.let { client ->
                // `nextFollowUp` is already collected at the top of
                // this screen — pass it through so the sheet can warn
                // about replacing an existing pending follow-up.
                ScheduleFollowUpSheet(
                    clientName = client.name,
                    existingFollowUp = nextFollowUp,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onConfirm = { result ->
                        viewModel.scheduleFollowUp(
                            scheduledAt = result.scheduledAt,
                            reason = result.reason,
                            replacePending = result.replacePending,
                        )
                        activeSheet = ActiveSheet.None
                    },
                )
            }
        }

        // Note: AddNoteSheet has been replaced by an inline QuickNoteInline
        // composable that lives above the activity timeline. The modal
        // sheet model was costing one tap per note plus the friction of
        // a sliding panel — for call-center agents who take notes in
        // every other call, that's expensive.


        // Auto-call countdown is driven by the unified LaunchedEffect
        // above the Scaffold (tick-down + instant-fire branches).
    }
}

/**
 * Timeline-first PreCall layout. Replaces the prior "hero + cards"
 * stack with a chronological activity feed that mirrors how the agent
 * actually thinks about the client: "what happened with them last,
 * and what should I say now."
 *
 * Vertical structure (top → bottom, all inside one scrolling LazyColumn):
 *  1. Compact header — avatar + name + phone + status chip + stats line.
 *  2. Contextual banner (only when there's something contextual to say:
 *     scheduled follow-up today, wrong-number warning, converted, etc.).
 *  3. SIP-readiness banner (only when SIP is not ready — same as before).
 *  4. Personal-data collapsible card (default collapsed — secondary info).
 *  5. Activity timeline — heterogeneous events (calls, notes, lead-import
 *     anchor) sorted newest-first, with day-bucket headers.
 *
 * The bottom bar (call CTA + quick actions) is owned by the outer
 * Scaffold and isn't part of this composable.
 */

/**
 * Read-only client info panel — header (avatar/name/status/data grid)
 * + activity timeline, with NO interactive call actions, NO note
 * input, and NO bottom bar. Designed to be embedded inside other
 * screens (currently used by [InCallScreen] so the agent can read
 * the client's context while talking).
 *
 * Uses its own [PreCallViewModel] instance — `key` is suffixed with
 * "readonly-" so it lives independently from the one that
 * [PreCallScreen] would create for the same `clientId` (avoids state
 * leaks between the two surfaces). Both share the underlying Room
 * flows, so updates appear in both instances reactively.
 *
 * Status/interest pills are kept clickable but the handlers are
 * no-ops — clicking won't open a sheet. We intentionally do NOT
 * disable them visually (would make the panel feel "different")
 * but they do nothing on tap. If/when a use case emerges to allow
 * status changes mid-call, we can add an optional click callback.
 */
@Composable
fun PreCallReadOnlyPanel(
    clientId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: PreCallViewModel =
        hiltViewModel<PreCallViewModel, PreCallViewModel.Factory>(
            key = "readonly-$clientId",
        ) { factory: PreCallViewModel.Factory -> factory.create(clientId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val showFullActivityHistory by viewModel.showFullActivityHistory
        .collectAsStateWithLifecycle()

    androidx.compose.material3.Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
            uiState.client == null -> ErrorState(
                message = uiState.errorMessage
                    ?: stringResource(R.string.precall_client_not_found),
                onBack = null,
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                val visibleActivity = remember(showFullActivityHistory, activity) {
                    if (showFullActivityHistory) activity
                    else activity.filterIsInstance<ActivityEvent.NoteEntry>()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item("readonly_header") {
                        CompactHeader(
                            client = uiState.client!!,
                            onBack = null,
                            onStatusClick = { /* read-only — no sheet */ },
                        )
                    }
                    item("readonly_activity_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 16.dp,
                                    bottom = 8.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.precall_activity_history),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.precall_activity_record_count,
                                    visibleActivity.size,
                                    visibleActivity.size,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (visibleActivity.isEmpty()) {
                        item("readonly_empty") {
                            ActivityEmptyState(
                                showAll = showFullActivityHistory,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    } else {
                        renderActivityTimeline(visibleActivity)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreCallContent(
    client: Client,
    activity: List<ActivityEvent>,
    nextFollowUp: FollowUp?,
    callReadiness: CallReadiness,
    isSubmittingNote: Boolean,
    showFullActivityHistory: Boolean,
    onRetrySip: () -> Unit,
    onSaveNote: (String) -> Unit,
    onBack: (() -> Unit)?,
    onRequestStatusChange: () -> Unit,
    onRequestSchedule: () -> Unit,
    contentPadding: PaddingValues,
) {
    // Timeline view-mode is sourced from Settings (not toggled here).
    // When showFullActivityHistory == false we filter to NoteEntry only.
    val visibleActivity = remember(showFullActivityHistory, activity) {
        if (showFullActivityHistory) activity
        else activity.filterIsInstance<ActivityEvent.NoteEntry>()
    }

    LazyColumn(
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("header") {
            CompactHeader(
                client = client,
                onBack = onBack,
                onStatusClick = onRequestStatusChange,
                onScheduleClick = onRequestSchedule,
            )
        }

        // SIP readiness banner — unchanged, still surfaces directly
        // under the header.
        val showReadiness = callReadiness !is CallReadiness.Ready &&
            callReadiness !is CallReadiness.Unknown
        if (showReadiness) {
            item("call_readiness_banner") {
                CallReadinessBanner(
                    state = callReadiness,
                    onRetry = onRetrySip,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Contextual banner — chooses ONE message based on client state.
        // Returns null and renders nothing for "ordinary" clients.
        val context = computeBannerContext(client, nextFollowUp, activity.size)
        if (context != null) {
            item("contextual_banner") {
                ContextualBanner(
                    context = context,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Personal data lives ENTIRELY in CompactHeader's data grid
        // now — Cédula / Tel / Salario / Estado as monospace fields,
        // no separate collapsible card. The duplication of a "Personal
        // data" expander below the header was confusing and against
        // the reference mockup.

        item("activity_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.precall_activity_history),
                    style = MaterialTheme.typography.labelMedium,
                    // Neutral gray — section header, not a colour accent.
                    // The mockup uses a quiet label here; primary was
                    // reading as "this is colourful UI" instead of
                    // "this is a section title".
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.precall_activity_record_count,
                        visibleActivity.size,
                        visibleActivity.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // (Toggle "Todo / Solo notas" moved to Settings.)

        // Quick note input — sits at the top of the timeline so any new
        // note the agent types lands at the position they expect:
        // chronologically newest.
        item("quick_note") {
            QuickNoteInline(
                isSubmitting = isSubmittingNote,
                onSave = onSaveNote,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (visibleActivity.isEmpty()) {
            item("activity_empty") {
                ActivityEmptyState(
                    showAll = showFullActivityHistory,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        } else {
            renderActivityTimeline(visibleActivity)
        }
    }
}

/**
 * Renders the activity timeline as a vertical visual rail: a 2-dp
 * connector line runs floor-to-ceiling on the left, with a colored
 * dot at each event's y-position and the card content to the right.
 *
 * Color semantics on the dots:
 *  - Top item (`index == 0` and event is *not* a [LeadImported]):
 *    green, hollow ring → "tu próxima acción / posición actual".
 *  - Older recent items (within the same first bucket / today):
 *    primary tint solid → currently relevant.
 *  - All other items: muted surface variant solid → historical.
 *
 * The bucket headers ("HOY", "AYER", "12 OCT") sit BETWEEN the rail
 * and the card column so they don't break the vertical line; instead
 * we draw a short tick mark across the rail at the bucket boundary.
 */
private fun LazyListScope.renderActivityTimeline(events: List<ActivityEvent>) {
    // Per-day bucket headers ("Today", "Yesterday"…) were removed —
    // each card already carries its own absolute/relative timestamp in
    // the top-right corner, so the extra label was visual noise. The
    // continuous left rail + connector dots provides the temporal
    // grouping affordance.
    events.forEachIndexed { index, event ->
        item(event.stableKey) {
            ActivityRail(
                event = event,
                isFirst = index == 0,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * One row of the activity rail: dot/line gutter on the left + card
 * on the right. The vertical connector line is drawn as a 2-dp Box
 * in the gutter column whose height fills the row — that way
 * adjacent rows visually stack into a continuous line without any
 * Canvas coordinate juggling.
 */
@Composable
private fun ActivityRail(
    event: ActivityEvent,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ActivityRailGutter(
            isFirst = isFirst,
            event = event,
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier
            .weight(1f)
            // 6dp top + 6dp bottom = 12dp gap between adjacent cards
            // without breaking the continuous rail line behind them.
            .padding(vertical = 6.dp)) {
            ActivityRow(event = event)
        }
    }
}

/**
 * The left-hand gutter that carries the vertical rail + dot. Computes
 * dot color/style from event kind and position (first / lead-import /
 * everything else).
 */
@Composable
private fun ActivityRailGutter(
    isFirst: Boolean,
    event: ActivityEvent,
) {
    val (dotColor, dotIsHollow) = when {
        // Top-most non-anchor event = "you are here". Hollow GREEN
        // ring — the only coloured dot in the rail (mockup convention:
        // "verde = lo importante / lo nuevo"). Anchors (LeadImported,
        // AssignedToAgent) never get the ring even when they're first,
        // because they're context markers, not fresh actions.
        isFirst &&
            event !is ActivityEvent.LeadImported &&
            event !is ActivityEvent.AssignedToAgent ->
            com.project.vortex.callsagent.ui.theme.Emerald600 to true
        // Anchor markers — muted.
        event is ActivityEvent.LeadImported ||
            event is ActivityEvent.AssignedToAgent ->
            MaterialTheme.colorScheme.outline to false
        // All past events render as neutral grey dots — the mockup
        // shows uniform muted dots below the green ring, no
        // differentiation by event type. Type info already lives
        // inside the card (icon + label), so the rail dot doesn't
        // need to carry it again.
        else -> MaterialTheme.colorScheme.onSurfaceVariant to false
    }
    // Gutter = thin vertical connector line + dot. The line runs the
    // full row height behind the dot; adjacent rows stack their lines
    // into a continuous rail. `outlineVariant` keeps it subtle (the
    // "clarita" the mockup shows) — visible enough to read as a
    // timeline, quiet enough not to compete with the cards.
    val railColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Connector line — 1dp wide, full-row-height, centered.
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(railColor),
        )
        // Dot container — always 10dp so the hollow ring fits, plus
        // gives the smaller solid dot a constant centering anchor.
        // Avatar center inside the card sits at 6dp (wrapper) +
        // 14dp (card padding) + 12dp (half of 24dp avatar) = 32dp
        // from row top. Container top = 32 - 5 = 27dp.
        Box(
            modifier = Modifier
                .padding(top = 27.dp)
                .size(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (dotIsHollow) {
                // Green "you are here" hollow ring — 10dp outer.
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            } else {
                // Past-event dot — 6dp solid (smaller than the green
                // ring) so the "current position" marker stays the
                // heaviest visual element in the rail.
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}

// ─── New timeline-first components ─────────────────────────────────────

/**
 * Compact identity strip — replaces the old gradient mega-hero. One
 * row with avatar + name + phone + status pill. The stats line below
 * summarises attempts/last-call/outcome in plain text so the agent
 * gets the at-a-glance summary without a 340dp gradient block stealing
 * the viewport.
 */
@Composable
private fun CompactHeader(
    client: Client,
    onBack: (() -> Unit)?,
    onStatusClick: () -> Unit,
    onScheduleClick: (() -> Unit)? = null,
) {
    // Compact-width adaptation: on phones, the header would otherwise
    // overflow horizontally (long names wrapping into 3+ lines, big
    // typography) and slide under the status bar (the parent Scaffold
    // zeroes its `contentWindowInsets` so we own the inset here). We
    // apply `statusBarsPadding` AND scale-down typography only when
    // the available width is Compact (<600dp).
    val isCompact = WindowSize.isCompactWidth
    val nameStyle = if (isCompact) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.headlineMedium
    }
    val avatarSize = if (isCompact) 48.dp else 56.dp

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                // Reserve the status-bar height so the title doesn't
                // slide under the system status icons on edge-to-edge.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // Top row: back button + circular avatar (kept consistent
            // with the ClientCard in the list — circular, not the
            // squared style of the reference mockup).
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                }
                Avatar(name = client.name, size = avatarSize)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // Source data often comes in ALL-CAPS; normalise
                        // to Title Case so the name reads as a person,
                        // not a shouted label. Helper is local to this
                        // file — see [toTitleCase].
                        text = client.name.toTitleCase(),
                        style = nameStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = nameStyle.lineHeight,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusPillCompact(client = client, onClick = onStatusClick)
                    }
                }
                // Calendar action — only shown when scheduling makes
                // sense for the current client. Terminal statuses
                // (CONVERTED, REMOVED) hide the button — those clients
                // can't be scheduled (D1).
                if (onScheduleClick != null && client.status.canBeScheduled()) {
                    IconButton(onClick = onScheduleClick) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(R.string.precall_schedule_follow_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Data grid — full client info per the mockup. Fields that
            // exist in the model but have no value render with "—"
            // so the layout stays consistent and the agent learns the
            // shape of "what we know about this client" by glance.
            // REF (loan reference) is NOT a model field — it only
            // appears when the backend supplied it via `extraData`,
            // otherwise omitted entirely.
            //
            // SALARIO uses the success accent color (green) — the
            // mockup's convention for "money figure". All other values
            // stay in the default `onSurface` tone.
            // Field labels resolved at the call site (composable) so the
            // grid follows the active locale; keyed into `remember` so the
            // list rebuilds when either the client or the labels change.
            val labelCedula = stringResource(R.string.precall_field_cedula)
            val labelPhone = stringResource(R.string.precall_field_phone)
            val labelSalary = stringResource(R.string.precall_field_salary)
            val labelSocialSecurity = stringResource(R.string.precall_field_social_security)
            val labelReference = stringResource(R.string.precall_field_reference)
            val dataFields = remember(
                client,
                labelCedula,
                labelPhone,
                labelSalary,
                labelSocialSecurity,
                labelReference,
            ) {
                buildList {
                    add(DataFieldSpec(labelCedula,
                        client.cedula?.takeIf { it.isNotBlank() } ?: "—",
                        accent = false))
                    add(DataFieldSpec(labelPhone, client.phone, accent = false))
                    add(DataFieldSpec(labelSalary,
                        client.salary?.let { formatSalary(it) } ?: "—",
                        accent = client.salary != null))
                    add(DataFieldSpec(labelSocialSecurity,
                        client.ssNumber?.takeIf { it.isNotBlank() } ?: "—",
                        accent = false))
                    (client.extraData["loanReference"] as? String)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(DataFieldSpec(labelReference, it, accent = false)) }
                }
            }
            // Order per mockup: data grid lives BETWEEN the name/pill
            // row and the divider — divider separates the header
            // block from the HISTORIAL section, NOT the name from
            // the data.
            Spacer(Modifier.height(12.dp))
            ClientDataGrid(fields = dataFields)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Per-field row spec for the header data grid. `accent` flips the
 * value's text colour to the success palette (green) so the field
 * reads as the "money" emphasis line — only SALARIO uses it today,
 * and only when the value is real (not the "—" placeholder).
 */
private data class DataFieldSpec(
    val label: String,
    val value: String,
    val accent: Boolean,
)

/**
 * Normalises an ALL-CAPS / arbitrary-case name string into Title Case.
 * Treats whitespace as the word separator. Spanish connectors (de, la,
 * del, los, las, y, e) stay lowercase except when they're the first
 * word, so "JUAN DE LA CRUZ" → "Juan de la Cruz".
 */
private fun String.toTitleCase(): String {
    val lowercaseConnectors = setOf("de", "la", "los", "las", "del", "y", "e")
    return trim()
        .split(Regex("\\s+"))
        .mapIndexed { index, word ->
            val lower = word.lowercase()
            if (index > 0 && lower in lowercaseConnectors) lower
            else lower.replaceFirstChar { it.uppercase() }
        }
        .joinToString(" ")
}

/**
 * Two-column grid of `LABEL: value` pairs with monospace values —
 * meant to render terse persistent client data (cedula, phone,
 * salary). Labels use the standard tracking-letter look (uppercase,
 * small, primary tint); values use the platform monospace family so
 * digits align column-to-column. Adapts to 1 or 2 columns based on
 * available width — but in this app the header is always wider than
 * 480dp so the 2-col layout always wins.
 */
@Composable
private fun ClientDataGrid(fields: List<DataFieldSpec>) {
    // Column count adapts to available width:
    //  - Compact (<600dp, typical phones): 2 columns. Three fields
    //    in one row was overflowing — phone numbers and cédulas wrap
    //    awkwardly. Two columns gives each field ~140-160dp, enough
    //    for the longest values without forcing a wrap.
    //  - Medium / Expanded (tablets): 3 columns matches the mockup
    //    (CÉDULA · TEL · SALARIO in row 1, SEG. SOCIAL · REF in row 2).
    val columns = if (WindowSize.isCompactWidth) 2 else 3
    val rows = fields.chunked(columns)

    rows.forEachIndexed { rowIndex, rowFields ->
        if (rowIndex > 0) Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            rowFields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.width(20.dp))
                DataField(
                    label = field.label,
                    value = field.value,
                    accent = field.accent,
                    modifier = Modifier.weight(1f),
                )
            }
            // Pad trailing slots so column widths stay aligned across
            // rows (last row may have fewer fields than `columns`).
            repeat(columns - rowFields.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DataField(
    label: String,
    value: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.precall_field_label_format, label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            // Accent rows render in success-green to catch the eye —
            // reserved for "money figure" semantics (SALARIO). All
            // other values stay in `onSurface` for neutral reading.
            color = if (accent) com.project.vortex.callsagent.ui.theme.Emerald600
                    else MaterialTheme.colorScheme.onSurface,
            // Single line + ellipsis: if a value is unusually long
            // (e.g. unformatted phone with country code), we'd rather
            // truncate than wrap and break the grid alignment.
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * "Estado" entry-point pill. Always reads the literal word "Estado"
 * plus a `▾` chevron — standard dropdown affordance: shows the CURRENT
 * selection ("Pending", "Interested", "Converted"…) plus the chevron
 * to signal "tap to change". `ClientStatus` is never null (PENDING is
 * the default on creation), so the pill always renders a real value
 * — no "Estado" placeholder needed. The status row was therefore
 * removed from the data grid below to eliminate the duplication.
 */
@Composable
private fun StatusPillCompact(client: Client, onClick: () -> Unit) {
    // Override the global status palette LOCALLY to match the mockup,
    // without affecting how status pills look on the list / agenda
    // screens. PENDING here renders in amber (the mockup convention
    // "needs attention"); other states fall back to the global theme
    // palette. Label comes from the shared [ClientStatus.label] helper
    // so the 5-state vocabulary stays consistent app-wide.
    val palette = precallStatusPalette(client.status)
    val label = client.status.label()
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = palette.container,
        contentColor = palette.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.onContainer),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "▾",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Local PENDING-amber palette override for PreCall — matches the
 * mockup's "needs attention" pill colour without touching the global
 * status palette (which other screens rely on for neutral pills).
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun precallStatusPalette(status: ClientStatus): StatusPalette = when (status) {
    ClientStatus.PENDING -> precallAmberPalette()
    else -> status.palette()
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun precallAmberPalette(): StatusPalette =
    if (MaterialTheme.colorScheme.surface.precallLuminance() < 0.5f) {
        StatusPalette(
            container = com.project.vortex.callsagent.ui.theme.Amber600.copy(alpha = 0.25f),
            onContainer = com.project.vortex.callsagent.ui.theme.Amber100,
        )
    } else {
        StatusPalette(
            container = com.project.vortex.callsagent.ui.theme.Amber100,
            onContainer = com.project.vortex.callsagent.ui.theme.Amber600,
        )
    }

private fun Color.precallLuminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

// ─── Contextual banner ────────────────────────────────────────────────

/**
 * Computed banner state. Picks the FIRST applicable context so we
 * never stack banners. Priority order is intentional — terminal
 * statuses dominate (a REMOVED client's scheduled callback is
 * meaningless).
 */
private sealed interface BannerContext {
    data object Converted : BannerContext
    data class Removed(val reason: RemovalReason?) : BannerContext
    data class ScheduledCallback(val at: Instant) : BannerContext
    data object NewLead : BannerContext
}

private fun computeBannerContext(
    client: Client,
    nextFollowUp: FollowUp?,
    activityCount: Int,
): BannerContext? = when {
    client.status == ClientStatus.CONVERTED -> BannerContext.Converted
    client.status == ClientStatus.REMOVED -> BannerContext.Removed(client.removalReason)
    nextFollowUp != null -> BannerContext.ScheduledCallback(nextFollowUp.scheduledAt)
    activityCount == 0 && client.callAttempts == 0 -> BannerContext.NewLead
    else -> null
}

@Composable
private fun ContextualBanner(context: BannerContext, modifier: Modifier = Modifier) {
    val (icon, container, onContainer, title, subtitle) = when (context) {
        BannerContext.Converted -> BannerStyle(
            icon = Icons.Filled.CheckCircle,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            title = stringResource(R.string.precall_banner_converted_title),
            subtitle = stringResource(R.string.precall_banner_converted_subtitle),
        )
        is BannerContext.Removed -> BannerStyle(
            icon = Icons.Filled.Block,
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
            title = stringResource(R.string.precall_banner_do_not_call_title),
            // Subtitle surfaces the concrete removal reason when the
            // backend supplied one (REMOVED always carries a
            // RemovalReason in the new model); falls back to the
            // generic opt-out copy if it's ever null.
            subtitle = context.reason?.label()
                ?: stringResource(R.string.precall_banner_do_not_call_subtitle),
        )
        is BannerContext.ScheduledCallback -> BannerStyle(
            icon = Icons.Filled.Schedule,
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            title = stringResource(
                R.string.precall_banner_scheduled_callback_title,
                formatScheduledRelative(context.at),
            ),
            subtitle = formatTimestamp(context.at),
        )
        BannerContext.NewLead -> BannerStyle(
            icon = Icons.Filled.Star,
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(R.string.precall_banner_new_lead_title),
            subtitle = stringResource(R.string.precall_banner_new_lead_subtitle),
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = onContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private data class BannerStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val container: Color,
    val onContainer: Color,
    val title: String,
    val subtitle: String,
)

@Composable
private fun formatScheduledRelative(at: Instant): String {
    val now = Instant.now()
    // Business clock — see BusinessConfig. The "today / tomorrow /
    // overdue" labels must match the admin/client calendar; otherwise
    // an agent in Caracas sees a follow-up land in a different bucket
    // than the admin who scheduled it.
    val zone = BusinessConfig.BUSINESS_TIMEZONE
    val today = LocalDate.now(zone)
    val target = at.atZone(zone).toLocalDate()
    return when {
        target == today && at.isAfter(now) ->
            stringResource(R.string.precall_relative_today)
        target == today && at.isBefore(now) ->
            stringResource(R.string.precall_relative_overdue)
        target == today.plusDays(1) ->
            stringResource(R.string.precall_relative_tomorrow)
        target.isAfter(today) -> {
            val days = ChronoUnit.DAYS.between(today, target).toInt()
            pluralStringResource(R.plurals.precall_relative_in_days, days, days)
        }
        else -> stringResource(R.string.precall_relative_overdue)
    }
}

// ─── Personal data (collapsible) ──────────────────────────────────────

@Composable
private fun PersonalDataCollapsible(client: Client, modifier: Modifier = Modifier) {
    var expanded by remember(client.id) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (if (expanded) "▾ " else "▸ ") +
                        stringResource(R.string.precall_personal_data),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) stringResource(R.string.precall_hide)
                    else stringResource(R.string.precall_tap_expand),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(16.dp)) {
                    val rows = buildList {
                        client.cedula?.takeIf { it.isNotBlank() }
                            ?.let { add("Cédula" to it) }
                        client.ssNumber?.takeIf { it.isNotBlank() }
                            ?.let { add("Social Security" to it) }
                        client.salary?.let { add("Salary" to formatSalary(it)) }
                        client.extraData.entries.filter { it.value != null }.forEach {
                            add(
                                it.key.replaceFirstChar { c -> c.uppercase() } to
                                    it.value.toString(),
                            )
                        }
                    }
                    rows.forEachIndexed { index, (label, value) ->
                        if (index > 0) {
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Activity rows ────────────────────────────────────────────────────

@Composable
private fun ActivityRow(event: ActivityEvent, modifier: Modifier = Modifier) {
    when (event) {
        is ActivityEvent.Call -> CallActivityRow(event, modifier)
        is ActivityEvent.NoteEntry -> NoteActivityRow(event, modifier)
        is ActivityEvent.FollowUpScheduled -> FollowUpActivityRow(event, modifier)
        is ActivityEvent.StatusChanged -> StatusChangeActivityRow(event, modifier)
        is ActivityEvent.LeadImported -> LeadImportedRow(modifier)
        is ActivityEvent.AssignedToAgent -> AssignedToAgentRow(event, modifier)
    }
}

/**
 * Status transition from the canonical history (any actor). Reuses the
 * shared [ActivityRowPlain] shell: title "Cambio de estado", the author /
 * source as meta, and "from → to" (+ optional reason) as the body — so the
 * agent can see who moved the client and why, mixed into the timeline.
 */
@Composable
private fun StatusChangeActivityRow(event: ActivityEvent.StatusChanged, modifier: Modifier) {
    // When the client was removed, append the reason ("Desistido · No
    // localizable") so the agent sees *why* — not just *that* it dropped.
    val toLabel = event.removalReason
        ?.let { "${event.toStatus.label()} · ${it.label()}" }
        ?: event.toStatus.label()
    val transition = if (event.fromStatus != null) {
        "${event.fromStatus.label()} → $toLabel"
    } else {
        toLabel
    }
    val body = event.reason?.let { "$transition\n\"$it\"" } ?: transition
    val author = event.changedByName
    val role = event.changedByRole?.let { statusChangeRoleLabel(it) }
    val meta = when {
        author != null && role != null -> "$author · $role"
        author != null -> author
        else -> event.source?.let { statusChangeSourceLabel(it) }
    }
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_status_change),
        meta = meta,
        timestamp = formatActivityTimestamp(event.occurredAt),
        body = body,
        icon = Icons.Filled.SwapHoriz,
        modifier = modifier,
    )
}

@Composable
private fun statusChangeRoleLabel(role: com.project.vortex.callsagent.common.enums.Role): String =
    stringResource(
        when (role) {
            com.project.vortex.callsagent.common.enums.Role.ADMIN -> R.string.precall_role_admin
            com.project.vortex.callsagent.common.enums.Role.AGENT -> R.string.precall_role_agent
        },
    )

@Composable
private fun statusChangeSourceLabel(
    source: com.project.vortex.callsagent.common.enums.StatusChangeSource,
): String = stringResource(
    when (source) {
        com.project.vortex.callsagent.common.enums.StatusChangeSource.INITIAL_LOAD ->
            R.string.precall_source_initial_load
        com.project.vortex.callsagent.common.enums.StatusChangeSource.CALL_OUTCOME ->
            R.string.precall_source_call_outcome
        com.project.vortex.callsagent.common.enums.StatusChangeSource.AGENT_OUT_OF_BAND ->
            R.string.precall_source_agent_out_of_band
        com.project.vortex.callsagent.common.enums.StatusChangeSource.ADMIN_MANUAL ->
            R.string.precall_source_admin_manual
        com.project.vortex.callsagent.common.enums.StatusChangeSource.ADMIN_REACTIVATE ->
            R.string.precall_source_admin_reactivate
        com.project.vortex.callsagent.common.enums.StatusChangeSource.AGENT_DISMISSAL ->
            R.string.precall_source_agent_dismissal
        com.project.vortex.callsagent.common.enums.StatusChangeSource.AGENT_DISMISSAL_UNDONE ->
            R.string.precall_source_agent_dismissal_undone
    },
)

/**
 * Single call event rendered as plain text on the screen background
 * (no Card wrapper) — matches the reference mockup where the timeline
 * reads as a continuous log, not a stack of cards.
 *
 * Layout:
 *   [Phone-icon avatar][Llamada · 2:34]                  [HOY/AYER, h:mm a]
 *                       [Human description of outcome]
 *
 * The title is the literal "Llamada" — we explicitly DON'T show
 * "Sistema" anymore because (a) agentId isn't tracked locally, so the
 * old label was a placeholder, and (b) the icon already communicates
 * the type, making a generic author redundant.
 */
@Composable
private fun CallActivityRow(event: ActivityEvent.Call, modifier: Modifier) {
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_call),
        meta = formatDuration(event.durationSeconds),
        timestamp = formatActivityTimestamp(event.occurredAt),
        // Use the centralized [CallOutcome.label] helper so the call
        // log speaks the same 13-value vocabulary as the rest of the
        // app (PostCall grid, agenda) instead of a local narrative map.
        body = event.outcome.label(),
        icon = Icons.Filled.Phone,
        modifier = modifier,
    )
}

/**
 * Single note event — same shape as [CallActivityRow] but with the
 * "Nota" title and the notes icon. Avatar styling is intentionally
 * uniform across event types (see [ActivityRowPlain]).
 */
@Composable
private fun NoteActivityRow(event: ActivityEvent.NoteEntry, modifier: Modifier) {
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_note),
        meta = null,
        timestamp = formatActivityTimestamp(event.occurredAt),
        body = event.content,
        icon = Icons.AutoMirrored.Filled.Notes,
        modifier = modifier,
    )
}

/**
 * Shared card layout for call and note events. Lives inside the
 * activity rail and renders inside a rounded Surface — matches the
 * reference mockup where each event reads as its own grouped block,
 * not free-floating text.
 *
 * Layout inside the card:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ [Icon avatar]  Title · meta              HOY, 14:20      │
 *   │                                                          │
 *   │ Body text spanning the full card width, multi-line ok.   │
 *   └──────────────────────────────────────────────────────────┘
 *
 * `title` is the type label ("Nota", "Llamada"); `meta` is an optional
 * secondary fragment after a `·` separator (e.g. call duration). The
 * avatar shows an icon matching the event type, set by the caller.
 */
@Composable
private fun ActivityRowPlain(
    title: String,
    meta: String?,
    timestamp: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // `surfaceContainerLow` (one step above `background`) gives the
        // subtle card-on-canvas separation the mockup shows in dark
        // theme without competing with content cards above.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        // 1-dp outline using `outlineVariant` — matches the mockup's
        // subtle card edge. Without the border the cards bled into
        // the background in dark theme at low container contrast.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header row: avatar + title/meta on the left, timestamp
            // pinned to the right. Avatar styling is uniform across
            // event types — a neutral dark circle with the type icon
            // in `onSurfaceVariant`. Differentiation comes from the
            // ICON inside the circle (phone, note…), not the color of
            // the circle, mirroring the mockup.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventTypeAvatar(
                    icon = icon,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = title,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (meta != null) {
                    Text(
                        text = "  ·  $meta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                // Muted body color (onSurfaceVariant) is intentional:
                // it sets the visual hierarchy header→body, matching
                // the reference mockup. Using `onSurface` (~white in
                // dark theme) flattens the hierarchy and the title
                // stops "leading" the eye.
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }
    }
}

/**
 * Small (~28dp) circular avatar with an icon representing the activity
 * TYPE (note, call, follow-up…). Previously rendered author initials,
 * but since the schema doesn't carry agentId per event yet, an icon is
 * both more honest ("this is a call") and visually richer than the
 * deterministic-hash-color initials of a "Sistema" / "Nota" string.
 *
 * Color comes from the caller so each event type can pick a tone that
 * matches its semantic family (primary/note, secondary/call, etc.).
 */
@Composable
private fun EventTypeAvatar(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun FollowUpActivityRow(event: ActivityEvent.FollowUpScheduled, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.precall_followup_scheduled),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = formatTimestamp(event.scheduledFor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun LeadImportedRow(modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Emoji stays inline (non-translatable symbol); only the
            // label text is localised.
            text = "✨  " + stringResource(R.string.precall_lead_imported),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Assignment event rendered as a regular timeline card — same shape
 * as [CallActivityRow] and [NoteActivityRow] (avatar circle with
 * type icon, title, timestamp top-right, body line). Tells the
 * agent "this is when the admin handed you this client" with the
 * same visual weight as the rest of the timeline so it doesn't
 * feel like a stray annotation.
 */
@Composable
private fun AssignedToAgentRow(
    event: ActivityEvent.AssignedToAgent,
    modifier: Modifier,
) {
    ActivityRowPlain(
        title = stringResource(R.string.precall_activity_assigned_title),
        meta = null,
        timestamp = formatActivityTimestamp(event.occurredAt),
        body = stringResource(R.string.precall_activity_assigned_body),
        icon = Icons.Filled.PersonAdd,
        modifier = modifier,
    )
}


@Composable
private fun ActivityEmptyState(showAll: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "✨", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (showAll) {
                stringResource(R.string.precall_empty_all_title)
            } else {
                stringResource(R.string.precall_empty_notes_title)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (showAll) {
                stringResource(R.string.precall_empty_all_body)
            } else {
                stringResource(R.string.precall_empty_notes_body)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

private fun formatSalary(amount: Double): String {
    // Whole-number USD when possible (most seeded values are integers)
    // — falls back to two decimals otherwise.
    val whole = amount.toLong()
    return if (amount == whole.toDouble()) "$%,d".format(whole)
    else "$%,.2f".format(amount)
}


/**
 * Bottom action bar — single primary CTA + at most one contextual
 * secondary button.
 *
 * Layout precedence (only one secondary slot is visible at a time):
 *  1. **Countdown active** → "Pausar" replaces everything else. Stopping
 *     the auto-dial is the only sensible action while a countdown ticks.
 *  2. **Auto-call session, no countdown** → Skip on the left, Call in
 *     the middle, Descartar on the right.
 *  3. **Normal (no auto-call)** → Call on the left taking most width,
 *     Descartar on the right.
 *
 * Status change and Wrong-number do NOT live here — Status moved to
 * the pill in the header; Wrong-number is recorded organically via
 * a call's outcome (or via Descartar with the "wrong number known
 * out-of-band" dismissal reason).
 */
@Composable
private fun CallActionBar(
    client: Client?,
    inAutoCall: Boolean,
    countdownSecondsLeft: Int?,
    onCall: () -> Unit,
    onSkip: () -> Unit,
    onPauseAutoCall: () -> Unit,
    onDismiss: () -> Unit,
    callEnabled: Boolean = true,
) {
    // Match Home's `NavigationRail` container so the bottom bar in
    // PreCall reads as the same surface stratum as the side rail the
    // agent sees on tablet. Material 3's `NavigationRail` defaults to
    // `surface` (NOT `surfaceContainer` — that's NavigationBar's
    // default, an intentional inconsistency in the M3 spec). Mirroring
    // `surface` here keeps the chrome uniform on the form factor we
    // actually ship (Tab A9+). `shadowElevation` stays so the bar
    // still floats above the scrollable content.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Union with WindowInsets.ime so the bottom bar lifts above
                // the soft keyboard when QuickNoteInline (or any field) is
                // focused. Scaffold reserves the bottomBar's measured height
                // in its content slot, so the LazyColumn automatically gets
                // the extra bottom padding and can scroll to its last item
                // even with the IME open. Without the union, the bar would
                // remain pinned to the nav-bar inset and the IME would cover
                // both the bar AND the bottom of the scrollable content.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left slot: Skip is visible during the WHOLE auto-call
            // session — including while the countdown is running.
            // The three actions are non-overlapping:
            //   - Skip   → advance to next client WITHOUT calling
            //   - Pausar → cancel the countdown, stay on this client
            //   - Llamar → fire the call now (bypass countdown)
            // The agent might want to pre-emptively skip during the
            // 3-second window if they realise the client isn't a
            // good moment to call.
            if (inAutoCall) {
                SecondaryActionButton(
                    label = stringResource(R.string.precall_action_skip),
                    onClick = onSkip,
                )
            }

            // Center: primary Call CTA. Single-line horizontal layout
            // matching the reference mockup exactly:
            //   [icon]  LLAMAR  6268-2021
            // — phone icon (dark, 18dp), bold "LLAMAR" verb, then the
            // phone number in a slightly muted weight to push it to
            // a secondary visual register. Dark text on PhoneGreen
            // (not white) — the reference shows the higher-contrast
            // dark-on-saturated-green palette.
            // Compact-width adaptation: phones have ~360-400dp total;
            // with Descartar reserving 96dp + paddings, the LLAMAR
            // button has ~200dp of usable content width. The mockup
            // sizes (titleMedium icon + label + monospace phone) were
            // overflowing the phone digit, clipping the last char. On
            // compact we shrink the icon, drop the label one notch,
            // and constrain the phone text to a single line with
            // ellipsis so the layout never breaks. On tablets we
            // keep the larger mockup sizing.
            val isCompactBar = WindowSize.isCompactWidth
            val callIconSize = if (isCompactBar) 16.dp else 18.dp
            val callLabelStyle = if (isCompactBar) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            }
            val callMetaStyle = if (isCompactBar) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            }
            val callSpacerLeft = if (isCompactBar) 8.dp else 10.dp
            val callSpacerMid = if (isCompactBar) 6.dp else 8.dp

            Button(
                onClick = onCall,
                enabled = client != null && callEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = if (isCompactBar) 12.dp else 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneGreen,
                    // Dark content over the saturated green — matches
                    // the reference mockup screenshot. White was too
                    // low-contrast given PhoneGreen's mid-tone.
                    contentColor = Color.Black,
                ),
            ) {
                Icon(
                    // Outlined variant matches the reference mockup —
                    // the filled phone glyph was reading too heavy
                    // against the saturated green container.
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(callIconSize),
                )
                Spacer(Modifier.width(callSpacerLeft))
                Text(
                    text = stringResource(R.string.precall_action_call),
                    style = callLabelStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(callSpacerMid))
                // Phone number (or countdown when auto-call is running)
                // in lower-emphasis weight + alpha so the verb leads
                // the eye and the number reads as metadata. `weight(1f)`
                // so it gets whatever horizontal space remains;
                // single-line + ellipsis prevents wrap/clipping if the
                // number is longer than the slot.
                val secondary = if (countdownSecondsLeft != null) {
                    stringResource(
                        R.string.precall_call_countdown,
                        client?.phone.orEmpty(),
                        countdownSecondsLeft,
                    )
                } else {
                    client?.phone.orEmpty()
                }
                Text(
                    text = secondary,
                    style = callMetaStyle,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            // Right slot: Pausar wins during countdown; otherwise
            // Descartar is the universal "remove from queue" action,
            // rendered as a compact icon+label square button to match
            // the reference mockup's bottom strip.
            if (countdownSecondsLeft != null) {
                SecondaryActionButton(
                    label = stringResource(R.string.precall_action_pause),
                    onClick = onPauseAutoCall,
                )
            } else {
                SquareIconActionButton(
                    icon = Icons.Filled.Close,
                    label = stringResource(R.string.precall_action_dismiss),
                    // Dropped the "cliente" secondary line — the
                    // icon + DESCARTAR label already conveys it, and
                    // the secondary text was overflowing the 60dp
                    // button height (icon 16 + spacer 4 + two
                    // labelSmall rows + vertical padding = ~68dp).
                    secondaryLabel = null,
                    onClick = onDismiss,
                    enabled = client != null,
                )
            }
        }
    }
}

/**
 * Secondary text-only action (Skip / Pausar) in the call bar. Filled
 * tonal style — `secondaryContainer` gives the button a real tinted
 * fill (Material 3 default), so it reads as a proper button rather
 * than a neutral surface tile.
 */
@Composable
private fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        // Fixed 72dp to align with the Call CTA and Descartar in the
        // same bar — visual coherence across all three tiles.
        modifier = Modifier.height(60.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        // Material 3 defaults: secondaryContainer / onSecondaryContainer.
        // No override — that was the whole point: get the real tonal
        // tint instead of an invisible neutral.
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Compact bottom-bar action button with icon on top + **two text
 * lines** below. Used for "Descartar" on PreCall to match the
 * reference mockup's stacked layout (icon, primary label, secondary
 * label).
 *
 * Outlined style — transparent button with a neutral border and
 * neutral labels. The X icon stays tinted with `error` (red): the
 * only red element in the button. Splitting roles this way keeps
 * the button visually quiet (border + text = neutral) while the
 * destructive semantic lives in the single most prominent glyph.
 *
 * Colors are **theme-derived** (`onSurface` + `outline`) so the
 * button reads correctly in both dark mode (light text/border on
 * the dark bar) and light mode (dark text/border on the white bar).
 * Hardcoded `Color.White` rendered invisible labels under the
 * white surface of the light palette.
 */
@Composable
private fun SquareIconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    secondaryLabel: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            // Slightly wider to fit two text lines comfortably.
            .width(96.dp)
            // Fixed 60dp — matches the Call CTA in the same bar.
            .height(60.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        // Content default = onSurface (text labels inherit this).
        // The Icon below overrides with `error` for the destructive X.
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            width = 1.dp,
            // `outline` is the M3 token for non-accent borders that
            // respects both palettes: medium-gray on white surfaces,
            // medium-gray on dark surfaces. No `Color.White` hardcode.
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                // 16dp matches the Phone icon in the sibling Call CTA —
                // both buttons read at the same visual weight now.
                modifier = Modifier.size(16.dp),
                // Explicit override — the only colour-coded element
                // in the button. Border and text stay neutral
                // (`onSurface` / `outline`) so the destructive signal
                // concentrates in this icon glyph.
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    // Muted neutral — `onSurfaceVariant` keeps the
                    // secondary line in the theme-derived "lower
                    // priority" register, readable on both light and
                    // dark surfaces. Replaces the previous
                    // `Color.White @ 70%` hardcode that vanished
                    // against the white surface of the light palette.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Inline note composer pegged to the top of the activity timeline.
 *
 * Visual match to the reference mockup:
 *  - A single rounded box with a **dashed border** — communicates
 *    "open input" without competing with the cards underneath.
 *  - Multi-line text area fills the body.
 *  - Bottom strip *inside the same box*: character count on the left,
 *    "+ Guardar nota" CTA on the right.
 *
 * **Two-state pattern** (mirrors the list-screen SearchField):
 *  - **Idle** — no `BasicTextField` is mounted at all. The box is a
 *    clickable Surface showing the placeholder. Because there's
 *    nothing focusable inside, the IME cannot pop on initial mount
 *    of the PreCall screen (which previously stole focus and lifted
 *    the keyboard the moment the screen appeared).
 *  - **Active** — full `BasicTextField` with auto-focus + `IME show`
 *    once layout has settled. Collapses back to idle when the agent
 *    blurs the field while it's empty.
 *
 * `text` is local state; on a successful save (`isSubmitting` flips
 * true→false while text was non-empty) the field auto-clears AND
 * collapses back to Idle, releasing the keyboard.
 */
@Composable
private fun QuickNoteInline(
    isSubmitting: Boolean,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shared state — outlives both branches so post-save cleanup can
    // toggle Active→Idle without losing the text/wasSubmitting state.
    var text by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(false) }
    var wasSubmitting by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSubmitting) {
        if (wasSubmitting && !isSubmitting && text.isNotEmpty()) {
            // Save succeeded: clear text, drop IME, return to Idle.
            text = ""
            keyboardController?.hide()
            isActive = false
        }
        wasSubmitting = isSubmitting
    }

    // Subtle lime-green dashed border — signals "this is an active
    // input zone for new content" while staying quiet enough not to
    // compete with the bold Call CTA or the activity cards underneath.
    // Brighter Emerald (400) at 45% alpha reads as "lime" in dark
    // theme, distinct from the saturated Emerald600 used for the
    // "you are here" rail dot and the SALARIO accent.
    val borderColor = NoteInputDashedColor.copy(alpha = 0.45f)

    if (!isActive) {
        // ── IDLE ──
        // Visually identical to Active (same dashed box, same
        // placeholder, same character counter "0 CARACTERES", same
        // disabled "+ GUARDAR NOTA" pill) — but with NO TextField
        // and NO focus targets, so the screen mount cannot steal
        // focus and pop the keyboard. A tap anywhere on the box
        // flips us to Active.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .drawDashedBorder(color = borderColor)
                .clickable { isActive = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.precall_note_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                )
                Spacer(Modifier.height(8.dp))
                IdleNoteBottomStrip()
            }
        }
        return
    }

    // ── ACTIVE ──
    var hasReceivedFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawDashedBorder(color = borderColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !isSubmitting,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasReceivedFocus = true
                            return@onFocusChanged
                        }
                        // Collapse back to Idle ONLY after focus has
                        // landed once AND the field is empty. The
                        // first-frame onFocusChanged fires with
                        // isFocused=false BEFORE our requestFocus
                        // resolves; without the latch we'd snap back
                        // to Idle on mount and never accept input.
                        if (hasReceivedFocus && text.isBlank()) {
                            isActive = false
                        }
                    },
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.precall_note_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.precall_note_char_count,
                        text.length,
                        text.length,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSave(text) },
                    enabled = !isSubmitting && text.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isSubmitting) {
                            stringResource(R.string.precall_note_saving)
                        } else {
                            stringResource(R.string.precall_note_save)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // Request focus + show IME exactly once on Active mount. See the
    // SearchField in ClientsScreen for the full rationale on why
    // `awaitFrame()` is required before calling `requestFocus()`.
    LaunchedEffect(Unit) {
        awaitFrame()
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

/**
 * Static visual sibling of the Active mode's bottom strip — used in
 * Idle so the box has the same height/density in both states (avoids
 * a layout shift when toggling). The save button is force-disabled
 * because there's no text to save in Idle.
 */
@Composable
private fun IdleNoteBottomStrip() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.precall_note_char_count, 0, 0),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { /* never invoked — button is disabled in Idle */ },
            enabled = false,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            shape = PillShape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.precall_note_save),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Modifier extension that paints a dashed rounded-rect border behind
 * the content. Used by [QuickNoteInline] to get the mockup's
 * "scratchpad" look without bringing in a full painter.
 */
private fun Modifier.drawDashedBorder(
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(12f, 8f),
            phase = 0f,
        ),
    )
    val inset = strokeWidth.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            size.width - 2 * inset,
            size.height - 2 * inset,
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            cornerRadius.toPx(),
        ),
        style = stroke,
    )
}

/**
 * Compact filter-chip-style button used inside [AddNoteSheet] and the
 * quick-action row above the bottom call bar. Single-line label,
 * pill-shaped, no toggle state — pure action trigger.
 */
@Composable
private fun QuickReplyChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (onBack != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.precall_go_back))
                }
            }
        }
    }
}

// 12-hour clock with AM/PM suffix — matches the rest of the app
// (AgendaScreen, PostCallScreen, ScheduleFollowUpSheet all use
// `h:mm a`). Built per-call with the process locale so AM/PM and
// month names follow the language chosen at runtime (Activities are
// recreated on language change — see ui/locale/LocaleContext).
private fun timeOnlyFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

private fun dateMonthFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())

/**
 * Absolute timestamp suited to a chronological activity log:
 *  - Same day: "HOY, 2:20 p. m."
 *  - Yesterday: "AYER, 9:15 a. m."
 *  - This week: "MAR, 2:20 p. m." (weekday abbrev + time)
 *  - Older: "12 OCT, 11:02 a. m."
 *
 * The agent gets factual time without having to mentally subtract
 * "hace X horas". Useful for audit trails too.
 */
@Composable
private fun formatActivityTimestamp(instant: Instant): String {
    // Business clock — "HOY"/"AYER" labels match the admin calendar.
    // See BusinessConfig.
    val zone = BusinessConfig.BUSINESS_TIMEZONE
    val now = LocalDate.now(zone)
    val zoned = instant.atZone(zone)
    val date = zoned.toLocalDate()
    val time = timeOnlyFormatter().format(zoned)
    return when {
        date == now -> stringResource(R.string.precall_ts_today_format, time)
        date == now.minusDays(1) ->
            stringResource(R.string.precall_ts_yesterday_format, time)
        ChronoUnit.DAYS.between(date, now) in 2..6 -> {
            val day = date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                .uppercase().take(3)
            "$day, $time"
        }
        else -> "${dateMonthFormatter().format(zoned).uppercase()}, $time"
    }
}

private fun timestampFormatter(): DateTimeFormatter =
    // 12-hour clock — see timeOnlyFormatter rationale above.
    DateTimeFormatter.ofPattern("d MMM, h:mm a", java.util.Locale.getDefault())

private fun formatTimestamp(instant: Instant): String =
    // Business clock everywhere — see BusinessConfig.
    timestampFormatter().format(instant.atZone(BusinessConfig.BUSINESS_TIMEZONE))

/**
 * Whether the agent can schedule a follow-up for a client in this
 * status. Scheduling only makes sense while the client is still active
 * in the funnel — the terminal states (CONVERTED, REMOVED) are
 * excluded; the agent has to reactivate first (admin path) before
 * scheduling.
 *
 * D1 from the design discussion: only the active states
 * (PENDING / INTERESTED / CITED) are schedulable from the mobile
 * agent's perspective.
 */
private fun ClientStatus.canBeScheduled(): Boolean = when (this) {
    ClientStatus.PENDING,
    ClientStatus.INTERESTED,
    ClientStatus.CITED -> true
    ClientStatus.CONVERTED,
    ClientStatus.REMOVED -> false
}

