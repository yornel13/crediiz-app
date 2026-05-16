package com.project.vortex.callsagent.presentation.precall.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.ui.components.InterestLevelSelector
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette

/**
 * Result of the [AgentStatusChangeSheet]. Carries the new status the
 * agent picked, an optional free-form reason, and — only when [status]
 * is INTERESTED — the thermometer level they chose.
 */
data class AgentStatusChange(
    val status: ClientStatus,
    val reason: String?,
    val level: InterestLevel?,
)

/**
 * Bottom sheet for HOW_IT_WORKS §7 — the agent moves a client to a
 * new status **without** placing a call. Common case: client wrote on
 * WhatsApp asking to be removed → flip to `DO_NOT_CALL` with reason.
 *
 * Distinct from the dismissal flow (which always lands in `DISMISSED`):
 * this sheet lets the agent pick any of the four "closing" statuses
 * the funnel accepts as an out-of-band signal. The audit row created
 * server-side carries `source = AGENT_OUT_OF_BAND` + the reason.
 *
 * The list is intentionally short — only the four real out-of-band
 * cases. Reactivating clients or moving back to PENDING is admin
 * territory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentStatusChangeSheet(
    clientName: String,
    currentStatus: ClientStatus,
    onDismiss: () -> Unit,
    onConfirm: (AgentStatusChange) -> Unit,
) {
    var selectedStatus by remember { mutableStateOf<ClientStatus?>(null) }
    var selectedLevel by remember { mutableStateOf(InterestLevel.COLD) }
    /**
     * `null` = no chip selected yet.
     * non-null & != QUICK_REASON_OTHER = a pre-cooked reason chosen.
     * QUICK_REASON_OTHER = agent wants to type their own → text field
     * becomes the source of truth for [reason].
     */
    var selectedQuickReason by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }

    val destinations = remember(currentStatus) { destinationsFor(currentStatus) }
    val quickReasons = QUICK_REASONS_BY_STATUS[selectedStatus].orEmpty()
    val usingFreeText = selectedQuickReason == null ||
        selectedQuickReason == QUICK_REASON_OTHER

    // Backend invariant (REASON_REQUIRED_TARGETS in clients.service.ts):
    // CONVERTED and DO_NOT_CALL are critical audit-trail transitions —
    // the agent must justify them or the server rejects with 400.
    val reasonRequired = selectedStatus in REASON_REQUIRED_STATUSES
    val effectiveReason = when {
        selectedQuickReason != null && selectedQuickReason != QUICK_REASON_OTHER ->
            selectedQuickReason!!
        else -> reason.trim()
    }
    val reasonProvided = effectiveReason.isNotBlank()
    val canConfirm = selectedStatus != null && (!reasonRequired || reasonProvided)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cambiar estado sin llamar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Usa esta acción para canales externos (WhatsApp, " +
                        "presencial, etc.). Queda registrado en el historial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Nuevo estado",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                destinations.forEach { dest ->
                    DestinationRow(
                        destination = dest,
                        selected = selectedStatus == dest.status,
                        onSelect = {
                            // Reset the quick-reason selection when the
                            // destination changes — the chip set is
                            // per-status and a stale pick would either
                            // disappear from the UI or carry over a
                            // mismatched label.
                            if (selectedStatus != dest.status) {
                                selectedQuickReason = null
                                reason = ""
                            }
                            selectedStatus = dest.status
                        },
                    )
                }
            }

            if (selectedStatus == ClientStatus.INTERESTED) {
                InterestLevelSelector(
                    selected = selectedLevel,
                    onSelect = { selectedLevel = it },
                )
            }

            // Quick reasons — one-tap path for the common cases.
            // Only rendered when the destination has presets defined.
            if (quickReasons.isNotEmpty()) {
                QuickReasonGroup(
                    options = quickReasons,
                    selected = selectedQuickReason,
                    onSelect = { picked ->
                        selectedQuickReason = picked
                        if (picked != QUICK_REASON_OTHER) reason = ""
                    },
                )
            }

            // Free-form text field. Hidden unless:
            //  - the agent picked "Other", or
            //  - there are no quick reasons for this status (e.g. INTERESTED).
            if (usingFreeText) {
                OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= MAX_REASON_CHARS) reason = it },
                label = {
                    Text(if (reasonRequired) "Motivo *" else "Motivo (opcional)")
                },
                placeholder = { Text("Ej: Cliente lo solicitó por WhatsApp") },
                isError = reasonRequired && !reasonProvided,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (reasonRequired && !reasonProvided) {
                                "Obligatorio para este estado"
                            } else if (reasonRequired) {
                                "Motivo obligatorio"
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reasonRequired && !reasonProvided) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "${reason.length} / $MAX_REASON_CHARS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        val status = selectedStatus ?: return@Button
                        if (reasonRequired && !reasonProvided) return@Button
                        onConfirm(
                            AgentStatusChange(
                                status = status,
                                reason = effectiveReason.ifBlank { null },
                                level = if (status == ClientStatus.INTERESTED) selectedLevel else null,
                            ),
                        )
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) { Text("Confirmar") }
            }
        }
    }
}

@Composable
private fun DestinationRow(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = destination.status.palette()
    val border = if (selected) palette.onContainer else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) palette.container else MaterialTheme.colorScheme.surface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        border = BorderStroke(width = if (selected) 2.dp else 1.dp, color = border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = palette.onContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.status.label(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = destination.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Vertical radio group of preset reasons plus a final "Otro:
 * escribir abajo" option. Selecting "Otro" lets the parent reveal
 * the free-form text field.
 */
@Composable
private fun QuickReasonGroup(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Motivo rápido",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { label ->
            QuickReasonRow(
                label = label,
                selected = selected == label,
                onClick = { onSelect(label) },
            )
        }
        QuickReasonRow(
            label = "Otro: escribir abajo",
            selected = selected == QUICK_REASON_OTHER,
            onClick = { onSelect(QUICK_REASON_OTHER) },
        )
    }
}

@Composable
private fun QuickReasonRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = null, // row handles click
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/**
 * The set of out-of-band destinations offered to the agent. Filtered
 * by [currentStatus] so the list doesn't show the status the client
 * is already in.
 *
 * Order matches the funnel reading: forward progress first
 * (INTERESTED → CONVERTED), then closures.
 */
private fun destinationsFor(currentStatus: ClientStatus): List<Destination> =
    ALL_DESTINATIONS.filter { it.status != currentStatus }

private data class Destination(
    val status: ClientStatus,
    val description: String,
    val icon: ImageVector,
)

private val ALL_DESTINATIONS: List<Destination> = listOf(
    Destination(
        status = ClientStatus.INTERESTED,
        description = "Mostró interés por otro canal (WhatsApp, etc.)",
        icon = Icons.Filled.SentimentSatisfied,
    ),
    Destination(
        status = ClientStatus.CONVERTED,
        description = "Cerró la venta por otro canal.",
        icon = Icons.Filled.MonetizationOn,
    ),
    Destination(
        status = ClientStatus.REJECTED,
        description = "Dijo que no por otro canal.",
        icon = Icons.Filled.SentimentDissatisfied,
    ),
    Destination(
        status = ClientStatus.DO_NOT_CALL,
        description = "Pidió no volver a ser contactado (opt-out).",
        icon = Icons.Filled.Block,
    ),
)

private const val MAX_REASON_CHARS = 200

/**
 * Mirror of the backend's `REASON_REQUIRED_TARGETS` set in
 * `calls-core/src/clients/clients.service.ts`. Keep in sync — if the
 * backend adds another status to that set, the mobile must add it here
 * or the agent gets a confusing 400 with no UI hint.
 */
private val REASON_REQUIRED_STATUSES = setOf(
    ClientStatus.CONVERTED,
    ClientStatus.DO_NOT_CALL,
)

/**
 * Pre-cooked reason chips per destination — one tap for the >80%
 * happy path (WhatsApp opt-out, bank confirmed signature, etc.). The
 * `null` slot at the end means "Other: write below" and re-enables
 * the free-form text field.
 *
 * Adding a label here = cleaner reporting data. Avoid translating
 * these to English unless the backend is multi-language.
 */
private val QUICK_REASONS_BY_STATUS: Map<ClientStatus, List<String>> = mapOf(
    ClientStatus.DO_NOT_CALL to listOf(
        "WhatsApp: pidió no contactar",
        "SMS: pidió no contactar",
        "Email: pidió no contactar",
        "Familiar pidió no llamar",
    ),
    ClientStatus.CONVERTED to listOf(
        "Banco confirmó firma",
        "Cliente confirmó por WhatsApp",
        "Cita en sucursal completada",
    ),
)

/** Sentinel "Other / write below" entry in the quick-reason radio group. */
private const val QUICK_REASON_OTHER = "__OTHER__"
