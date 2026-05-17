package com.project.vortex.callsagent.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.data.sync.SyncResult
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.LoggedOut -> onLoggedOut()
            }
        }
    }

    // Settings is a form-like screen — content stretched edge-to-edge
    // on a 1300dp tablet leaves toggles unreachable from where the
    // operator's thumbs naturally rest. Cap to ~720dp on wide screens
    // and center it. On Compact (phone) keep edge-to-edge.
    val contentMaxWidth = if (WindowSize.isWideWidth) 720.dp else androidx.compose.ui.unit.Dp.Unspecified

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Landscape on tablets can't fit all cards + logout in one
                // viewport. Scroll keeps the bottom action (Sign out)
                // reachable without redesigning the screen as a grid.
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Inner column owns the actual content width. Decoupling
            // it from the scrollable outer column lets us center the
            // whole form without breaking scroll bounds.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            AccountCard(name = state.agentName, email = state.agentEmail)

            DisplayCard(
                themeMode = state.themeMode,
                onThemeSelect = viewModel::onThemeModeChange,
                keepScreenOn = state.keepScreenOn,
                onKeepScreenOnToggle = viewModel::onKeepScreenOnToggle,
                showFullActivityHistory = state.showFullActivityHistory,
                onShowFullActivityHistoryToggle = viewModel::onShowFullActivityHistoryToggle,
            )

            AutoAdvanceCard(
                enabled = state.autoAdvance,
                delaySeconds = state.autoCallDelaySeconds,
                onToggle = viewModel::onAutoAdvanceToggle,
                onDelayChange = viewModel::onAutoCallDelayChange,
            )

            SyncCard(
                pendingCount = state.pendingCount,
                isSyncing = state.isSyncing,
                lastResult = state.lastSync,
                onForceSync = {
                    viewModel.forceSync()
                    viewModel.refreshPendingCount()
                },
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }
            } // end inner content column
        }
    }
}

@Composable
private fun AccountCard(name: String, email: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Account")
            Text(
                text = name.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = email.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisplayCard(
    themeMode: ThemeMode,
    onThemeSelect: (ThemeMode) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    showFullActivityHistory: Boolean,
    onShowFullActivityHistoryToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Display")
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.values().forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeSelect(mode) },
                        label = { Text(themeLabel(mode)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Keep-screen-on toggle. The actual enforcement (Window flag
            // + login gate) lives in MainActivity — this control only
            // owns the user-facing intent.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep screen on",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Prevent the display from sleeping while the app is open and you are signed in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOnToggle,
                    colors = appSwitchColors(),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Activity-history view mode for PreCall timeline.
            // When false, only NoteEntry events show; calls/follow-ups
            // are filtered out. The PreCall ViewModel observes this
            // flow — no per-screen toggle.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mostrar historial completo",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Si está apagado, en PreCall verás solo las notas; las llamadas y otros eventos se ocultan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showFullActivityHistory,
                    onCheckedChange = onShowFullActivityHistoryToggle,
                    colors = appSwitchColors(),
                )
            }
        }
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

/**
 * Switch colours with EXPLICIT off-state values so the toggle stays
 * visible in dark mode. The Material 3 defaults use
 * `surfaceContainerHighest` for the track and `outline` for the
 * thumb, which in our dark theme collide visually with the card
 * background — the switch appeared "missing" when off.
 *
 * Overriding with `surfaceVariant` (track) and `onSurfaceVariant`
 * (thumb) guarantees the two colours are tonal opposites of each
 * other AND distinct from the card surface in both light and dark
 * palettes. The "on" colours stay at the M3 defaults (primary) so
 * the toggle's positive state still reads as the brand accent.
 */
@Composable
private fun appSwitchColors() = SwitchDefaults.colors(
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun AutoAdvanceCard(
    enabled: Boolean,
    delaySeconds: Int,
    onToggle: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Auto-Call Mode")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-advance",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (enabled) {
                            "Automatically dial the next client after No Answer / Busy."
                        } else {
                            "Always stop at the next client before calling."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = appSwitchColors(),
                )
            }

            // Countdown delay only matters when auto-advance is on. We
            // keep the row visible-but-disabled instead of hiding so
            // the agent can preview their setting without flipping the
            // toggle first.
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Countdown",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = if (delaySeconds == 0) "Off" else "$delaySeconds s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = if (delaySeconds == 0) {
                    "No countdown — the next client is dialed immediately."
                } else {
                    "Wait $delaySeconds second${if (delaySeconds == 1) "" else "s"} before dialing the next client."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = delaySeconds.toFloat(),
                onValueChange = { onDelayChange(it.toInt()) },
                valueRange = 0f..15f,
                steps = 14, // 16 stops total: 0..15
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun SyncCard(
    pendingCount: Int,
    isSyncing: Boolean,
    lastResult: SyncResult,
    onForceSync: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Synchronization")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Pending records", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "$pendingCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider()

            Text(
                text = when (val r = lastResult) {
                    SyncResult.Idle -> "No sync performed yet."
                    is SyncResult.Success -> buildString {
                        append("Last sync OK — ")
                        append(r.syncedInteractions).append(" calls, ")
                        append(r.syncedNotes).append(" notes, ")
                        append(r.syncedFollowUps).append(" follow-ups, ")
                        append(r.syncedCompletions).append(" completions.")
                    }
                    is SyncResult.Error -> "Last sync failed: ${r.message}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onForceSync,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSyncing) "Syncing..." else "Force sync")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}
