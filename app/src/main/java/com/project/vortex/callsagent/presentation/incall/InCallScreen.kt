package com.project.vortex.callsagent.presentation.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.call.model.CallUiState
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.presentation.precall.PreCallReadOnlyPanel
import com.project.vortex.callsagent.ui.components.Avatar
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import com.project.vortex.callsagent.ui.theme.Teal700
import com.project.vortex.callsagent.ui.theme.Teal900
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * How long the "Call ended" state stays visible on InCallScreen
 * after the SIP session disconnects, before the Activity finishes
 * and AppNavGraph routes to PostCall. Short enough that the agent
 * isn't waiting between calls; long enough to confirm visually that
 * the hang-up registered.
 */
private const val POST_DISCONNECT_HOLD_MS = 1_200L

@Composable
fun InCallScreen(
    onCallFinished: () -> Unit,
    viewModel: InCallViewModel = hiltViewModel(),
) {
    val callState by viewModel.callState.collectAsState()
    val liveClient by viewModel.currentClient.collectAsState()
    val direction by viewModel.callDirection.collectAsState()
    val incomingPhone by viewModel.incomingPhoneNumber.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaker by viewModel.isSpeakerOn.collectAsState()
    val liveNote by viewModel.liveNoteContent.collectAsState()

    // `CallController` nulls out `currentClient` the moment the SIP
    // session ends. Without caching, the InCallScreen would lose its
    // client mid-screen (during the post-disconnect hold), so the
    // split layout would collapse to single-panel and the right-side
    // info pane would vanish — making "InCall" and the "call-ending"
    // state render as visually different layouts. We latch the last
    // non-null client so the layout stays identical from the moment
    // the call starts until this Activity actually finishes.
    var stableClient by remember {
        mutableStateOf<com.project.vortex.callsagent.domain.model.Client?>(null)
    }
    if (liveClient != null) stableClient = liveClient
    val client = stableClient

    var hasDisconnected by remember { mutableStateOf(false) }
    if (callState is CallUiState.Disconnected) hasDisconnected = true

    LaunchedEffect(hasDisconnected) {
        if (hasDisconnected) {
            delay(POST_DISCONNECT_HOLD_MS)
            onCallFinished()
        }
    }

    // Responsive layout decision — matrix of width × height instead
    // of the previous "wide vs compact width" one-dimensional check.
    //
    //                  │ Compact height  │ Non-compact height
    //   ───────────────┼─────────────────┼────────────────────
    //   Compact width  │ Tabs            │ Vertical split
    //   Non-compact W  │ Tabs            │ Horizontal split (Row)
    //
    // - Horizontal split: tablet portrait/landscape, foldable open.
    //   Plenty of horizontal AND vertical room — two panes fit
    //   comfortably.
    // - Vertical split: phone portrait. Tall + narrow → stack the
    //   call panel on top, scrollable info below. Each pane gets
    //   real vertical space.
    // - Tabs: short screens (phone landscape, tablet with IME up).
    //   Neither horizontal nor vertical split would give either
    //   pane a usable size. Tabs let the agent switch between
    //   "Llamada" and "Cliente" without compromising either view.
    val layoutMode = when {
        WindowSize.isCompactHeight -> InCallLayoutMode.Tabs
        WindowSize.isCompactWidth -> InCallLayoutMode.VerticalSplit
        else -> InCallLayoutMode.HorizontalSplit
    }

    // If we don't have a client yet (very brief window during call
    // start), the right-pane / info-tab content has nothing to show.
    // Fall back to single-panel call UI to keep the agent looking at
    // something coherent.
    val hasClient = client != null

    Surface(modifier = Modifier.fillMaxSize()) {
        if (!hasClient) {
            CallControlPanel(
                client = client,
                fallbackPhone = incomingPhone.orEmpty(),
                direction = direction,
                callState = callState,
                liveNote = liveNote,
                isMuted = isMuted,
                isSpeakerOn = isSpeaker,
                onNoteChange = viewModel::onNoteChange,
                onToggleMute = viewModel::toggleMute,
                onToggleSpeaker = viewModel::toggleSpeaker,
                onEndCall = viewModel::endCall,
                modifier = Modifier.fillMaxSize(),
            )
        } else when (layoutMode) {
            InCallLayoutMode.HorizontalSplit -> Row(modifier = Modifier.fillMaxSize()) {
                CallControlPanel(
                    client = client,
                    fallbackPhone = incomingPhone.orEmpty(),
                    direction = direction,
                    callState = callState,
                    liveNote = liveNote,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeaker,
                    onNoteChange = viewModel::onNoteChange,
                    onToggleMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onEndCall = viewModel::endCall,
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                )
                PreCallReadOnlyPanel(
                    clientId = client!!.id,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                )
            }

            InCallLayoutMode.VerticalSplit -> Column(modifier = Modifier.fillMaxSize()) {
                // Call panel takes the top ~45% — enough for status
                // hero, the LiveNote textarea (shorter than wide-mode
                // since vertical space is split now), and the row of
                // controls. The info panel takes the remaining ~55%
                // with its own scroll for the timeline.
                CallControlPanel(
                    client = client,
                    fallbackPhone = incomingPhone.orEmpty(),
                    direction = direction,
                    callState = callState,
                    liveNote = liveNote,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeaker,
                    onNoteChange = viewModel::onNoteChange,
                    onToggleMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onEndCall = viewModel::endCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                )
                PreCallReadOnlyPanel(
                    clientId = client!!.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f),
                )
            }

            InCallLayoutMode.Tabs -> InCallTabsLayout(
                client = client,
                fallbackPhone = incomingPhone.orEmpty(),
                direction = direction,
                callState = callState,
                liveNote = liveNote,
                isMuted = isMuted,
                isSpeakerOn = isSpeaker,
                onNoteChange = viewModel::onNoteChange,
                onToggleMute = viewModel::toggleMute,
                onToggleSpeaker = viewModel::toggleSpeaker,
                onEndCall = viewModel::endCall,
            )
        }
    }
}

/**
 * Layout mode for [InCallScreen] derived from the current window
 * size class along both axes. See the matrix comment at the call
 * site for the full decision table.
 */
private enum class InCallLayoutMode { HorizontalSplit, VerticalSplit, Tabs }

/**
 * Tabbed in-call layout for short screens (phone landscape, tablet
 * with IME up). Default tab is "Llamada" so the agent can hang up
 * immediately — the "Cliente" tab is one tap away when they want
 * context. We deliberately do NOT pre-load both tab contents into
 * an off-screen state; the inactive tab unmounts to free RAM and
 * avoid background flows churning while invisible.
 */
@Composable
private fun InCallTabsLayout(
    client: Client?,
    fallbackPhone: String,
    direction: CallDirection,
    callState: CallUiState,
    liveNote: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onNoteChange: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
) {
    var selectedTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.incall_tab_call)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.incall_tab_client)) },
                enabled = client != null,
            )
        }
        when (selectedTab) {
            0 -> CallControlPanel(
                client = client,
                fallbackPhone = fallbackPhone,
                direction = direction,
                callState = callState,
                liveNote = liveNote,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                onNoteChange = onNoteChange,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onEndCall = onEndCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            1 -> client?.let {
                PreCallReadOnlyPanel(
                    clientId = it.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

/**
 * Left-pane (or full-screen on compact) call control panel.
 *
 * Same skeleton as PostCall's left panel:
 *   ┌────────────────┐
 *   │ Green hero     │   <- call identity (avatar, name, status, timer)
 *   ├────────────────┤
 *   │                │
 *   │ Theme body     │   <- live notes textarea + Mute/Speaker toggles
 *   │ (scrollable)   │
 *   │                │
 *   ├────────────────┤
 *   │ End call (red) │   <- primary CTA
 *   └────────────────┘
 *
 * **Why no more full-panel green gradient:** the previous version
 * had the entire panel saturated with Teal — InCall and PostCall
 * read as two completely different visual languages. Now the green
 * lives only in the hero strip (call identity) and the body is
 * theme-aware (light/dark adapts correctly). The two panels are
 * structurally identical, only their internal content differs.
 */
@Composable
private fun CallControlPanel(
    client: Client?,
    fallbackPhone: String,
    direction: CallDirection,
    callState: CallUiState,
    liveNote: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onNoteChange: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            // `imePadding()` shrinks this column from the bottom by
            // the IME's height whenever the soft keyboard is up. The
            // body (Column with `weight(1f)`) absorbs the loss — the
            // TextField gets shorter — so the EndCallCta at the
            // bottom of the panel STAYS visible above the keyboard.
            // Without this, the keyboard would cover the End button
            // and the Mute/Speaker row above it.
            .imePadding(),
    ) {
        // Green hero strip — keeps the "in-a-call" visual identity
        // concentrated at the top of the panel. Mirrors the
        // `CallEndedHero` of PostCall (same gradient, same layout).
        CallActiveHero(
            client = client,
            fallbackPhone = fallbackPhone,
            direction = direction,
            callState = callState,
        )
        // Notes textarea — same shape as PostCall's "Nota" field and
        // the textareas inside the bottom-sheets, so all four note
        // surfaces feel like one component:
        //  - `minLines = 4` → opens already as a multi-line box.
        //  - `maxLines = 8` → grows up to ~8 lines before scrolling
        //                     internally; never eats the whole panel.
        //  - label includes "(opcional)" so the agent knows it's not
        //    a required field.
        //
        // The `Spacer(weight=1f)` after the textfield pushes the
        // Mute/Speaker row + End CTA against the bottom edge while
        // keeping the textfield anchored just below the green hero —
        // exactly mirroring PostCall's vertical rhythm.
        OutlinedTextField(
            value = liveNote,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.incall_note_label)) },
            placeholder = { Text(stringResource(R.string.incall_note_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            minLines = 4,
            maxLines = 8,
            enabled = callState !is CallUiState.Disconnected,
        )
        Spacer(modifier = Modifier.weight(1f))
        // Mute / Speaker controls — always visible, pinned right
        // above the End CTA. See comment on the body Column for why
        // they're NOT inside it.
        MuteSpeakerRow(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            enabled = callState !is CallUiState.Disconnected,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Primary CTA at the bottom — symmetric to PostCall's Save
        // button (same position, same size, just red because the
        // action is destructive: terminate the call).
        EndCallCta(
            enabled = callState !is CallUiState.Disconnected,
            onClick = onEndCall,
        )
    }
}

/**
 * Green hero strip at the top of the in-call panel. Renders avatar,
 * name, phone, and the status+timer pill — all in white over the
 * Teal gradient. Status bar inset is handled here so the panel
 * itself doesn't need to know about safeDrawing.
 *
 * Visually paired with [com.project.vortex.callsagent.presentation.postcall.CallEndedHero]
 * — same gradient, same avatar+name layout, same pill structure.
 */
@Composable
private fun CallActiveHero(
    client: Client?,
    fallbackPhone: String,
    direction: CallDirection,
    callState: CallUiState,
) {
    val gradient = Brush.verticalGradient(colors = listOf(Teal700, Teal900))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (direction == CallDirection.INBOUND) {
            DirectionBadge(label = stringResource(R.string.incall_badge_incoming))
            Spacer(Modifier.height(8.dp))
        }
        Avatar(name = client?.name ?: "?", size = 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = client?.name
                ?: if (direction == CallDirection.INBOUND) stringResource(R.string.incall_unknown_number)
                else stringResource(R.string.incall_connecting),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = client?.phone?.takeIf { it.isNotBlank() } ?: fallbackPhone,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        StatusAndTimer(state = callState)
    }
}

/**
 * Mute + Speaker toggles, side by side, theme-aware.
 *
 * Replaces the previous round white-on-green buttons from the
 * `ControlsRow` — those only made sense when the whole panel was
 * green. Now they're `FilledTonalButton` circles in the theme's
 * secondary container, with the toggled state lighting up in the
 * primary palette.
 */
@Composable
private fun MuteSpeakerRow(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    enabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleControl(
            icon = if (isMuted) {
                androidx.compose.material.icons.Icons.Filled.MicOff
            } else {
                androidx.compose.material.icons.Icons.Filled.Mic
            },
            label = stringResource(R.string.incall_control_mute),
            checked = isMuted,
            enabled = enabled,
            onClick = onToggleMute,
        )
        ToggleControl(
            // Use AutoMirrored VolumeUp regardless of state — the
            // toggled state shows via the container colour change,
            // not via different icons. (Icons.Filled.VolumeUp is
            // deprecated in favour of the AutoMirrored variant for
            // RTL correctness.)
            icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
            label = stringResource(R.string.incall_control_speaker),
            checked = isSpeakerOn,
            enabled = enabled,
            onClick = onToggleSpeaker,
        )
    }
}

/**
 * End-call CTA — symmetric to PostCall's Save button at the bottom
 * of the panel. Full-width within the panel, error-coloured because
 * "End" is the destructive primary action of a call.
 */
@Composable
private fun EndCallCta(enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.CallEnd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.incall_hangup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─── Incoming ringing ─────────────────────────────────────────────────────

@Composable
private fun IncomingRingingContent(
    client: Client?,
    fallbackPhone: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        // Header pill — clearly marks this is incoming.
        Surface(
            shape = PillShape,
            color = Color.White.copy(alpha = 0.20f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.incall_incoming_call),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Avatar(name = client?.name ?: "?", size = 112.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = client?.name ?: stringResource(R.string.incall_unknown_number),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = client?.phone?.takeIf { it.isNotBlank() } ?: fallbackPhone,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.85f),
        )

        Spacer(Modifier.weight(1f))

        // Big accept / reject buttons.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleActionButton(
                icon = Icons.Filled.CallEnd,
                label = stringResource(R.string.incall_reject),
                color = MaterialTheme.colorScheme.error,
                onClick = onReject,
            )
            CircleActionButton(
                icon = Icons.Filled.Call,
                label = stringResource(R.string.incall_accept),
                color = PhoneGreen,
                onClick = onAccept,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(76.dp),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// (Old `ActiveCallContent` was deleted — its layout lived assumed a
// fully-green panel and is no longer used. The new `CallControlPanel`
// renders the hero+body+CTA inline.)

@Composable
private fun DirectionBadge(label: String) {
    Surface(
        shape = PillShape,
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatusAndTimer(state: CallUiState) {
    val statusLabel = when (state) {
        CallUiState.Idle -> stringResource(R.string.incall_status_idle)
        CallUiState.Dialing -> stringResource(R.string.incall_status_dialing)
        CallUiState.Ringing -> stringResource(R.string.incall_status_ringing)
        is CallUiState.Active -> stringResource(R.string.incall_status_connected)
        CallUiState.Disconnected -> stringResource(R.string.incall_status_call_ended)
    }

    Surface(
        shape = PillShape,
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state is CallUiState.Active) PhoneGreen else Color.White),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    if (state is CallUiState.Active) {
        LiveTimer(activeSince = state.activeSince)
    } else {
        Text(
            text = "—",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LiveTimer(activeSince: Instant) {
    var elapsed by remember { mutableStateOf(elapsedSeconds(activeSince)) }
    LaunchedEffect(activeSince) {
        while (true) {
            elapsed = elapsedSeconds(activeSince)
            delay(500)
        }
    }
    Text(
        text = formatElapsed(elapsed),
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Theme-aware toggle (Mute / Speaker). Replaces the previous
 * white-on-green version that only made sense over a fully-green
 * panel. Now uses `FilledTonalIconToggleButton` semantics — neutral
 * `secondaryContainer` when off, primary `primaryContainer` when on.
 */
@Composable
private fun ToggleControl(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val container = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val contentColor = if (checked) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Surface(
            shape = CircleShape,
            color = container,
            modifier = Modifier.size(56.dp),
            onClick = onClick,
            enabled = enabled,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun elapsedSeconds(from: Instant): Long =
    Duration.between(from, Instant.now()).seconds.coerceAtLeast(0)

private fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
