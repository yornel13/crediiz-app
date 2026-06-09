package com.project.vortex.callsagent.presentation.clients.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.ui.components.FullHeightBottomSheet
import com.project.vortex.callsagent.ui.theme.label

private const val MAX_REASON_LEN = 200

/**
 * Confirmation sheet for the dismissal action. Shows the 6 preset
 * reason chips plus a free-form `motivo` field — both inputs are
 * optional. The selected `reasonCode` and `freeFormReason` are
 * returned via [onConfirm]; the caller persists via the dismissal
 * repository.
 *
 * Implementation notes
 * ────────────────────
 * - Built on [FullHeightBottomSheet] (consistent with every other
 *   form sheet — opens at full height, no half-expanded state).
 * - Scrollable body + sticky footer (Cancelar / Descartar) so the
 *   CTAs stay visible with the IME open or long content.
 * - All copy in Spanish (Latin American, tú-form) per
 *   `language_style_latin_american` memory entry.
 *
 * The label is intentionally **"Motivo (opcional)"** and NOT
 * "Nota (Opcional)": the value is the `reason` field of
 * `ClientDismissalEntity`, not a first-class `Note`. See the
 * architectural note in the dismissal repository for the trade-off.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DismissClientSheet(
    clientName: String,
    onDismiss: () -> Unit,
    onConfirm: (removalReason: RemovalReason, freeFormReason: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedReason by rememberSaveable { mutableStateOf<RemovalReason?>(null) }
    var freeText by rememberSaveable { mutableStateOf("") }

    FullHeightBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        // Outer column owns the full sheet height. Body scrolls in the
        // middle, footer (Cancelar + Descartar) stays pinned so the
        // agent never loses the CTAs even with long content or the
        // IME open — same pattern as ScheduleFollowUpSheet.
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ─── Header ─────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.dismiss_client_title, clientName),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.dismiss_client_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ─── Preset chips ──────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.dismiss_client_reason_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RemovalReason.entries.forEach { reason ->
                            FilterChip(
                                selected = selectedReason == reason,
                                onClick = {
                                    selectedReason = if (selectedReason == reason) null else reason
                                },
                                label = { Text(reason.label()) },
                            )
                        }
                    }
                }

                // ─── Free-form reason ──────────────────────────────
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { if (it.length <= MAX_REASON_LEN) freeText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    placeholder = { Text(stringResource(R.string.dismiss_client_free_form_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.dismiss_client_char_count,
                                freeText.length,
                                MAX_REASON_LEN,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )

                // Bottom breathing room so the helper line doesn't
                // kiss the footer divider.
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }

            // ─── Sticky footer ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = {
                        val reason = selectedReason ?: return@Button
                        onConfirm(reason, freeText.trim().ifBlank { null })
                    },
                    enabled = selectedReason != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dismiss_client_confirm), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
