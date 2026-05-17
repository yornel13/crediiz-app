package com.project.vortex.callsagent.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.presentation.precall.PreCallScreen

/**
 * Adaptive detail pane wrapper.
 *
 * On wide layouts the detail side of the list/detail scaffold hosts the
 * SAME composable that owns the full-screen call flow — [PreCallScreen].
 * No visual duplication, no parallel ViewModel, no "lite" copy: the
 * agent works against the real thing. Hero, stats, personal data, notes
 * timeline, "Llamar"/"Skip" bottom bar, status-change sheet, dismiss
 * sheet, add-note sheet, auto-call countdown — everything PreCall
 * supports works here unmodified.
 *
 * The four navigation-flavored callbacks are remapped by the caller
 * (the adaptive screen) so they manipulate the scaffold's pane state
 * instead of pushing a new full-screen destination:
 *  - [onBack]: animate the divider back to list-full (no Nav-Compose pop).
 *  - [onSkipToNext]: re-target the scaffold's `Detail` contentKey to the
 *    next client (in-place pane swap, no navigation).
 *  - [onSkipToSummary] / [onExitAutoCall]: bridge to the existing
 *    full-screen routes — auto-call exhaustion and explicit exit are
 *    "leave the pane" moments that the rest of the app already handles.
 *
 * When [clientId] is null we render an idle placeholder. The Hilt
 * assisted-factory in [PreCallScreen] requires a non-null id, so we
 * gate on it explicitly.
 */
@Composable
fun ClientDetailPane(
    clientId: String?,
    onBack: () -> Unit,
    onSkipToNext: (String) -> Unit,
    onSkipToSummary: () -> Unit,
    onExitAutoCall: () -> Unit,
    modifier: Modifier = Modifier,
    showBackArrow: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (clientId == null) {
            DetailEmptyState()
        } else {
            // PreCallScreen owns the entire pre-call experience. We
            // mount it as-is, keyed by clientId via its assisted-inject
            // factory — selecting another client in the list triggers
            // a re-mount with a fresh ViewModel scoped to that id.
            //
            // The back-arrow lives inside the PreCall hero; we route
            // its tap to our onBack only when the divider is fully
            // collapsed to detail (i.e. there's nowhere visible to
            // "go back" to without re-expanding the list).
            PreCallScreen(
                clientId = clientId,
                // Null when the list is already visible alongside us —
                // the back arrow on the Hero stays hidden in that case
                // (nowhere meaningful to "go back" to). When the divider
                // is at detail-full the caller sets this to a real
                // callback that animates the divider back to list-full.
                onBack = if (showBackArrow) onBack else null,
                onSkipToNext = onSkipToNext,
                onSkipToSummary = onSkipToSummary,
                onExitAutoCall = onExitAutoCall,
            )
        }
    }
}

@Composable
private fun DetailEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Select a client to see details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Pick someone from the list on the left to open the call screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
