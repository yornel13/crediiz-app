package com.project.vortex.callsagent.presentation.precall.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.ui.components.FullHeightBottomSheet
import com.project.vortex.callsagent.ui.theme.label
import com.project.vortex.callsagent.ui.theme.palette

/**
 * Result of the [AgentStatusChangeSheet]. Carries the destination status,
 * an optional free-form reason, and — only when [status] is
 * [ClientStatus.REMOVED] — the mandatory [removalReason].
 */
data class AgentStatusChange(
    val status: ClientStatus,
    val removalReason: RemovalReason?,
    val reason: String?,
)

/**
 * Bottom sheet for the out-of-band status change (the agent moves a
 * client **without** placing a call — e.g. client wrote on WhatsApp).
 *
 * In the 5-state model the agent can only **advance** along the funnel
 * (high-water mark), so the destinations offered are the statuses above
 * the current one — INTERESTED → CITED → CONVERTED — plus REMOVED. A move
 * to REMOVED requires a [RemovalReason]. The backend has the final say:
 * a blocked transition comes back as a 200 no-op and the caller reconciles.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgentStatusChangeSheet(
    clientName: String,
    currentStatus: ClientStatus,
    onDismiss: () -> Unit,
    onConfirm: (AgentStatusChange) -> Unit,
) {
    var selectedStatus by remember { mutableStateOf<ClientStatus?>(null) }
    var selectedRemovalReason by remember { mutableStateOf<RemovalReason?>(null) }
    var reason by remember { mutableStateOf("") }

    val destinations = remember(currentStatus) { destinationsFor(currentStatus) }
    val isRemoval = selectedStatus == ClientStatus.REMOVED
    val removalReasonMissing = isRemoval && selectedRemovalReason == null
    val canConfirm = selectedStatus != null && !removalReasonMissing

    FullHeightBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.status_change_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = clientName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.status_change_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.status_change_new_status_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    destinations.forEach { dest ->
                        DestinationRow(
                            destination = dest,
                            selected = selectedStatus == dest.status,
                            onSelect = {
                                if (selectedStatus != dest.status) {
                                    selectedRemovalReason = null
                                }
                                selectedStatus = dest.status
                            },
                        )
                    }
                }

                // Removal reason — mandatory when the destination is REMOVED.
                if (isRemoval) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.status_change_removal_reason_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (removalReasonMissing) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RemovalReason.entries.forEach { rr ->
                                FilterChip(
                                    selected = selectedRemovalReason == rr,
                                    onClick = {
                                        selectedRemovalReason =
                                            if (selectedRemovalReason == rr) null else rr
                                    },
                                    label = { Text(rr.label()) },
                                )
                            }
                        }
                        if (removalReasonMissing) {
                            Text(
                                text = stringResource(R.string.status_change_removal_reason_required),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // Free-form reason — optional, useful for audit context.
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= MAX_REASON_CHARS) reason = it },
                    label = { Text(stringResource(R.string.status_change_reason_label_optional)) },
                    placeholder = { Text(stringResource(R.string.status_change_reason_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.status_change_char_counter,
                                reason.length,
                                MAX_REASON_CHARS,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

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
                        val status = selectedStatus ?: return@Button
                        if (status == ClientStatus.REMOVED && selectedRemovalReason == null) {
                            return@Button
                        }
                        onConfirm(
                            AgentStatusChange(
                                status = status,
                                removalReason = if (status == ClientStatus.REMOVED) {
                                    selectedRemovalReason
                                } else {
                                    null
                                },
                                reason = reason.trim().ifBlank { null },
                            ),
                        )
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.status_change_action_confirm)) }
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
                    text = stringResource(destination.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Out-of-band destinations offered to the agent: the funnel advances
 * **above** the current status (high-water mark — the agent never moves
 * a client backwards), plus REMOVED as the lateral exit.
 */
private fun destinationsFor(currentStatus: ClientStatus): List<Destination> {
    val currentRank = funnelRank(currentStatus)
    val advances = ADVANCE_DESTINATIONS.filter { funnelRank(it.status) > currentRank }
    return advances + REMOVED_DESTINATION
}

private fun funnelRank(status: ClientStatus): Int = when (status) {
    ClientStatus.PENDING -> 0
    ClientStatus.INTERESTED -> 1
    ClientStatus.CITED -> 2
    ClientStatus.CONVERTED -> 3
    ClientStatus.REMOVED -> -1 // lateral, not on the funnel scale
}

private data class Destination(
    val status: ClientStatus,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

private val ADVANCE_DESTINATIONS: List<Destination> = listOf(
    Destination(
        status = ClientStatus.INTERESTED,
        descriptionRes = R.string.status_change_dest_interested_desc,
        icon = Icons.Filled.SentimentSatisfied,
    ),
    Destination(
        status = ClientStatus.CITED,
        descriptionRes = R.string.status_change_dest_cited_desc,
        icon = Icons.Filled.Event,
    ),
    Destination(
        status = ClientStatus.CONVERTED,
        descriptionRes = R.string.status_change_dest_converted_desc,
        icon = Icons.Filled.MonetizationOn,
    ),
)

private val REMOVED_DESTINATION = Destination(
    status = ClientStatus.REMOVED,
    descriptionRes = R.string.status_change_dest_removed_desc,
    icon = Icons.Filled.Block,
)

private const val MAX_REASON_CHARS = 200
