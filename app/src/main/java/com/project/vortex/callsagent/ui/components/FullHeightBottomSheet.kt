package com.project.vortex.callsagent.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Thin wrapper over [ModalBottomSheet] that opens **fully expanded**
 * from the first paint, skipping Material 3's default "half-expanded"
 * intermediate state.
 *
 * Why this exists
 * ───────────────
 * The default `ModalBottomSheet` lands at ~50% of the screen and
 * forces the agent to drag up to see the rest of the form. In the
 * Crediiz flow most sheets host date/time pickers, a thermometer
 * selector, and a multi-line notes textarea — there's no way to fit
 * them in half a screen without truncating something. Every sheet
 * in this app should open at full height by default.
 *
 * Use this composable instead of bare `ModalBottomSheet` for any
 * sheet that hosts a multi-field form. Sheets with only one short
 * action (a confirm-only dialog) can still use `ModalBottomSheet`
 * directly, but consistency is cheap — prefer this wrapper.
 *
 * The agent can still dismiss the sheet by swiping down or tapping
 * outside; `skipPartiallyExpanded` only removes the **intermediate**
 * state, not the dismiss gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullHeightBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        content = content,
    )
}
