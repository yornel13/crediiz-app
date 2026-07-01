package com.project.vortex.callsagent.presentation.precall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.presentation.common.WindowSize
import com.project.vortex.callsagent.ui.theme.PhoneGreen
import com.project.vortex.callsagent.ui.theme.PillShape
import kotlinx.coroutines.android.awaitFrame

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
internal fun CallActionBar(
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
internal fun QuickNoteInline(
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
