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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.data.sync.SyncResult
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.ui.locale.AppLanguage
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
                appLanguage = state.appLanguage,
                onLanguageSelect = viewModel::onAppLanguageChange,
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
                Text(stringResource(R.string.settings_sign_out))
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
            SectionTitle(stringResource(R.string.settings_account))
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
    appLanguage: AppLanguage,
    onLanguageSelect: (AppLanguage) -> Unit,
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
            SectionTitle(stringResource(R.string.settings_display))
            Text(
                text = stringResource(R.string.settings_theme),
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

            // Language override. Changing it recreates the activity (see
            // LocaleAwareActivity) so all resources re-resolve immediately.
            Text(
                text = stringResource(R.string.settings_language_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.values().forEach { language ->
                    FilterChip(
                        selected = appLanguage == language,
                        onClick = { onLanguageSelect(language) },
                        label = { Text(languageLabel(language)) },
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
                        text = stringResource(R.string.settings_keep_screen_on),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_keep_screen_on_desc),
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
                        text = stringResource(R.string.settings_show_full_history),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_show_full_history_desc),
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

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.SPANISH -> R.string.settings_language_spanish
    }
)

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
            SectionTitle(stringResource(R.string.settings_auto_call_mode))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_auto_advance),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (enabled) {
                            stringResource(R.string.settings_auto_advance_desc_on)
                        } else {
                            stringResource(R.string.settings_auto_advance_desc_off)
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
                    text = stringResource(R.string.settings_countdown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = if (delaySeconds == 0) {
                        stringResource(R.string.settings_countdown_off)
                    } else {
                        stringResource(R.string.settings_countdown_seconds, delaySeconds)
                    },
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
                    stringResource(R.string.settings_countdown_desc_immediate)
                } else {
                    pluralStringResource(
                        R.plurals.settings_countdown_desc_wait,
                        delaySeconds,
                        delaySeconds,
                    )
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
            SectionTitle(stringResource(R.string.settings_synchronization))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_pending_records),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "$pendingCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider()

            Text(
                text = when (val r = lastResult) {
                    SyncResult.Idle -> stringResource(R.string.settings_sync_idle)
                    is SyncResult.Success -> stringResource(
                        R.string.settings_sync_success,
                        r.syncedInteractions,
                        r.syncedNotes,
                        r.syncedFollowUps,
                        r.syncedCompletions,
                    )
                    is SyncResult.Error -> stringResource(
                        R.string.settings_sync_error,
                        r.message,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onForceSync,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isSyncing) {
                        stringResource(R.string.settings_syncing)
                    } else {
                        stringResource(R.string.settings_force_sync)
                    }
                )
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
