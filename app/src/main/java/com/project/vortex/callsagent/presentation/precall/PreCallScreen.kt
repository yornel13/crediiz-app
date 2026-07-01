package com.project.vortex.callsagent.presentation.precall

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.domain.model.ActivityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.domain.call.CallReadiness
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.presentation.clients.components.DismissClientSheet
import com.project.vortex.callsagent.presentation.precall.components.AgentStatusChangeSheet
import com.project.vortex.callsagent.presentation.precall.components.QuotationCard
import com.project.vortex.callsagent.presentation.precall.components.QuotationSheet
import com.project.vortex.callsagent.presentation.precall.components.ScheduleFollowUpSheet
import com.project.vortex.callsagent.ui.components.CallReadinessBanner

private val HeroShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

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
    data object Quotation : ActiveSheet
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
                onRequestQuotation = { activeSheet = ActiveSheet.Quotation },
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
            ActiveSheet.Quotation -> uiState.client?.let { client ->
                QuotationSheet(
                    initial = client.quotation,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onConfirm = { bank, amount, biweekly, notes ->
                        viewModel.saveQuotation(
                            bank = bank,
                            quotedAmount = amount,
                            biweeklyPayment = biweekly,
                            notes = notes,
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
 * Embedded client context panel — reuses the EXACT same content list as
 * [PreCallScreen] via [PreCallContent] (header, contextual banner,
 * quotation, activity timeline), minus the interactive call-prep
 * affordances. Embedded in [InCallScreen] and [PostCallScreen] so the
 * agent keeps the full client context while talking / classifying.
 *
 * What's intentionally off here: SIP-readiness banner, note input,
 * back button, scheduling, and the bottom call bar. The status pill is
 * display-only — status changes happen exclusively in PreCall/detail.
 *
 * What IS interactive: the quotation card. Editing it calls this panel's
 * own [PreCallViewModel.saveQuotation]; the change lands in Room and
 * propagates to every surface observing this client.
 *
 * Uses its own [PreCallViewModel] instance — `key` is suffixed with
 * "readonly-" so it lives independently from the one that
 * [PreCallScreen] would create for the same `clientId` (avoids state
 * leaks between the two surfaces). Both share the underlying Room
 * flows, so updates appear in both instances reactively.
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
    val nextFollowUp by viewModel.nextFollowUp.collectAsStateWithLifecycle()
    val showFullActivityHistory by viewModel.showFullActivityHistory
        .collectAsStateWithLifecycle()

    var showQuotationSheet by remember { mutableStateOf(false) }

    // Defensive: if the client drops out mid-flow (e.g. InCall clears
    // currentClient on the post-disconnect hold), don't leave the
    // quotation sheet orphaned over an empty panel.
    if (uiState.client == null && showQuotationSheet) {
        showQuotationSheet = false
    }

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
            // Reuses the SAME content list as PreCallScreen — single source
            // of truth. Read-only here: status pill is display-only, the
            // quotation is editable (per product decision), and the SIP
            // banner / note input / back / schedule affordances are off.
            else -> PreCallContent(
                client = uiState.client!!,
                activity = activity,
                nextFollowUp = nextFollowUp,
                isSubmittingNote = uiState.isSubmittingNote,
                showFullActivityHistory = showFullActivityHistory,
                onRequestStatusChange = { /* display-only — changed in PreCall */ },
                onRequestQuotation = { showQuotationSheet = true },
                contentPadding = PaddingValues(bottom = 16.dp),
                callReadiness = null,
                onRetrySip = null,
                onSaveNote = null,
                onBack = null,
                onRequestSchedule = null,
            )
        }
    }

    // Quotation editing shares the read-only ViewModel's saveQuotation —
    // the change propagates to every panel observing this client via Room.
    if (showQuotationSheet) {
        uiState.client?.let { client ->
            QuotationSheet(
                initial = client.quotation,
                onDismiss = { showQuotationSheet = false },
                onConfirm = { bank, amount, biweekly, notes ->
                    viewModel.saveQuotation(
                        bank = bank,
                        quotedAmount = amount,
                        biweeklyPayment = biweekly,
                        notes = notes,
                    )
                    showQuotationSheet = false
                },
            )
        }
    }
}

@Composable
private fun PreCallContent(
    client: Client,
    activity: List<ActivityEvent>,
    nextFollowUp: FollowUp?,
    isSubmittingNote: Boolean,
    showFullActivityHistory: Boolean,
    onRequestStatusChange: () -> Unit,
    onRequestQuotation: () -> Unit,
    contentPadding: PaddingValues,
    // Optional call-prep affordances. When null, the related UI is hidden —
    // this lets the read-only embed ([PreCallReadOnlyPanel]) reuse the exact
    // same content list without the interactive SIP banner, note input, or
    // scheduling action. See the read-only call site for the wiring.
    callReadiness: CallReadiness? = null,
    onRetrySip: (() -> Unit)? = null,
    onSaveNote: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onRequestSchedule: (() -> Unit)? = null,
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

        // SIP readiness banner — only in the interactive call-prep surface
        // (callReadiness != null). The read-only embed passes null because
        // SIP setup already happened before the call started.
        val readiness = callReadiness
        if (readiness != null &&
            readiness !is CallReadiness.Ready &&
            readiness !is CallReadiness.Unknown
        ) {
            item("call_readiness_banner") {
                CallReadinessBanner(
                    state = readiness,
                    onRetry = onRetrySip ?: {},
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

        item("quotation_card") {
            QuotationCard(
                quotation = client.quotation,
                onEdit = onRequestQuotation,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

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
        // chronologically newest. Hidden in the read-only embed
        // (onSaveNote == null): the note is captured in the left panel
        // there (live note in InCall, post-call note in PostCall).
        if (onSaveNote != null) {
            item("quick_note") {
                QuickNoteInline(
                    isSubmitting = isSubmittingNote,
                    onSave = onSaveNote,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
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
