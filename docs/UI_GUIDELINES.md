# UI_GUIDELINES — Non-negotiable rules for new screens

**Audience:** anyone (human or agent) creating or modifying a Compose
screen, Activity, or top-level layout in `calls-agends`.

These rules exist because the same mistakes have recurred across multiple
screens. Read this before opening a `Scaffold` / `Activity` / `Column`
that fills the screen.

Last updated: **2026-04-25** (initial — anchored on the in-call insets
regression).

---

## Rule 1 — Always handle window insets explicitly

`enableEdgeToEdge()` is enabled in `MainActivity` and every `Activity`
that hosts our Compose UI. **The system bars (status / navigation /
display cutout) draw OVER our content unless we say otherwise.** The
result of skipping this: the page appears to "bleed under" the status
bar at the top and the gesture nav at the bottom, which we've already
fixed three times.

### Decision tree

| Pattern | What to use |
|---|---|
| Screen has a `Scaffold` (top/bottom bars) | `Scaffold` already pads its content with `WindowInsets.systemBars` by default. Done. |
| **Nested** `Scaffold` (e.g. inside HomeScreen tabs) | Add `contentWindowInsets = WindowInsets(0, 0, 0, 0)` to the inner Scaffold. The outer one already consumed the insets. |
| Full-bleed Composable (no Scaffold, e.g. gradient hero, splash) | Wrap content in `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`. The background can extend edge-to-edge; only the content respects insets. |
| Bottom action bar (sticky button) inside a custom Surface | `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` on the inner content. |
| TopAppBar drawn manually over a gradient | `Modifier.windowInsetsPadding(WindowInsets.statusBars)` on the bar's Row. |

### Imports

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
```

### Self-check before submitting a new screen

1. Does the top of the content sit **below** the status bar, or under it?
2. Does the bottom of the content sit **above** the gesture nav bar, or
   under it?
3. On a device with a notch/cutout, is anything important obscured?

If any answer is wrong, the screen ships broken. The user has had to
flag this on Clients tab, Pre-Call, Post-Call (early version), and
In-Call. **Don't make it four.**

---

## Rule 2 — Don't lock orientation unless explicitly requested

`android:screenOrientation` in the manifest and `requestedOrientation`
in code **stay unset by default**. Tablets (Tab A9+) work in both
landscape and portrait, and locking either way is a UX decision that
the user — not the dev — makes.

If a screen genuinely needs a single orientation (e.g. an immersive
video player), confirm with the user first. There is **no implicit
landscape lock** for "the tablet form factor" — that was an assumption
that already produced one rollback.

### Implication for layout

Compose layouts must work in both orientations:

- **Don't** assume horizontal space is plentiful; don't build `Row`-only
  splits as the primary layout.
- Prefer a single `Column` where the dominant content takes
  `Modifier.weight(1f)` and the rest stacks above/below.
- Use `BoxWithConstraints` only when there's a real responsive need
  (rare in this app).

---

## Rule 3 — Avoid state-derived `LaunchedEffect` keys for one-shot
finalize flows

**Pattern that breaks:**

```kotlin
// 🔴 BAD — if state flips Disconnected → Idle before delay completes,
// the effect is cancelled and the activity never finishes.
LaunchedEffect(callState) {
    if (callState is CallUiState.Disconnected) {
        delay(1200)
        onCallFinished()
    }
}
```

**Pattern that works:**

```kotlin
// 🟢 GOOD — latch the signal in remembered state so it can't be undone.
var hasDisconnected by remember { mutableStateOf(false) }
if (callState is CallUiState.Disconnected) hasDisconnected = true

LaunchedEffect(hasDisconnected) {
    if (hasDisconnected) {
        delay(1200)
        onCallFinished()
    }
}
```

The general principle: **if the effect represents a "commit point"
(navigate, finish activity, persist + dismiss), don't key it directly
to mutable upstream state**. Latch the trigger first, then key on the
latch.

---

## Rule 4 — Resource-grade defensive checks for "no active call"-type states

When an Activity hosts UI that depends on a singleton or shared state,
check for the degenerate case where the singleton is empty.

Example from `CallManager.disconnect()`:

```kotlin
fun disconnect() {
    val call = currentCall
    if (call != null) {
        call.disconnect()
    } else if (_callState.value !is CallUiState.Disconnected) {
        // No active call to drop, but the in-call UI is up. Surface
        // Disconnected so the activity can close itself.
        _callState.value = CallUiState.Disconnected
    }
}
```

Without that fallback, the End button in InCallActivity becomes a
no-op when the call dropped silently before reaching `STATE_ACTIVE` —
the user is stranded with a non-functional button.

Apply the same thinking to: "what if the client lookup returned null?",
"what if the interaction id doesn't exist anymore?", "what if the
SavedStateHandle arg is missing?". Surface a clean exit, never strand
the user.

---

## Rule 5 — When in doubt, show me the screen on the actual device

Emulators lie about insets, gesture-nav behaviour, and OEM quirks
(Samsung One UI is the relevant target here). A green build is not a
finished screen until it's been seen on the Tab A9+.

The quickest way to ship insets bugs is to declare a screen "done"
based on a Compose preview or emulator screenshot. Don't.
