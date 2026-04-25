# TARGET_DEVICE — Samsung Galaxy Tab A9+

> **This app is designed for tablets, not phones.** Primary target is the
> Samsung Galaxy Tab A9+. All layout, typography, and interaction decisions
> must assume an 11" screen held in **landscape**.

---

## 1. Device specs (reference)

| Spec | Value |
|---|---|
| Display | 11" LCD, 1920 × 1200 px, 90 Hz |
| Aspect ratio | 16:10 (≈ 1.6) |
| Density | ~206 dpi (`hdpi` bucket, but closer to `mdpi` / `tvdpi`) |
| Android | 13 out of the box (Samsung promises 2 OS upgrades) |
| SoC | Qualcomm Snapdragon 695 |
| RAM | 4 GB or 8 GB |
| Storage | 64 GB or 128 GB |
| Battery | 7040 mAh |
| Stylus | **No** (this is not an S-series tablet) |
| Cameras | 8 MP rear / 5 MP front — not used by this app |

### Two SKUs — **critical distinction**

| Model | ID | Cellular calls | Data | Implication for this app |
|---|---|---|---|---|
| WiFi-only | `SM-X210` | ❌ **No dialer, no SIM radio** | WiFi only | Native dialer path **will not work**. Must use VoIP. |
| 5G | `SM-X216` | ✅ Yes | 5G + WiFi | Native dialer works as designed. |

**→ Confirm with the product owner which SKU is in the agent fleet before
finalizing the call flow.** If it's a mix, the app needs a runtime check:

```kotlin
val hasTelephony = packageManager
    .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
```

If false, the UI must hide the "Call" button or route through a VoIP
backend (Twilio Programmable Voice, Vonage, SIP, etc.).

---

## 2. Orientation policy

- **Primary: landscape** (`screenOrientation="sensorLandscape"`).
- Portrait is acceptable as a secondary mode but **must not be the design
  target**. Never build a layout that only works in portrait.
- Lock orientation on the In-Call Screen to avoid jarring rotations during
  an active call.

Configure in `AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="sensorLandscape"
    ... />
```

---

## 3. Compose Window Size Classes

Use `androidx.compose.material3.windowsizeclass.WindowSizeClass` to pick
layouts. On the Tab A9+ in landscape:

| Class | Value | Meaning for our UI |
|---|---|---|
| `widthSizeClass` | `Expanded` (≥ 840 dp) | Two-pane layouts allowed: list + detail side by side. |
| `heightSizeClass` | `Medium` (480–900 dp) | Vertical space is okay but not generous. Avoid tall stacked cards; prefer horizontal layouts. |

### Layout rules derived from that

1. **Clients tab**: list on the left (40% width), Pre-Call detail on the
   right (60%) when an item is selected. Avoid full-screen navigation
   transitions when two-pane is viable.
2. **Agenda tab**: same master-detail pattern.
3. **In-Call Screen**: full-screen overlay. Live timer on one side, notes
   text area on the other. Do not stack vertically — waste of horizontal
   space.
4. **Post-Call Screen**: outcome grid 5 columns wide (not a vertical list
   like on phones). Follow-up form side-by-side with outcome selection.
5. **Settings**: categories on the left nav, details on the right.

---

## 4. Touch targets and typography

- Minimum touch target **56 dp** (larger than the Material 48 dp default).
  Agents may use the tablet resting on a desk, tapping with a single
  finger; larger targets reduce misses.
- Body text minimum **16 sp**.
- Outcome buttons, "Call" button, FAB: **at least 72 dp tall** — these are
  hit many times per day and must be obvious.
- Use Material 3 **tonal elevation** instead of shadows — LCD on Tab A9+
  doesn't render deep shadows well.

---

## 5. Permissions specific to the device path

For the **5G variant (cellular calling)** the manifest already declares:

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Android 13+ requires **runtime permission requests** for dangerous perms.
Group them into a single pre-flight dialog at first app launch; don't
fragment across screens.

For the **WiFi variant (VoIP)** you'll need **`RECORD_AUDIO`** and the
VoIP SDK's dependencies. The cellular perms above become no-ops.

---

## 6. Connectivity assumptions

- Tab A9+ 5G has cellular data but agents are expected to be under WiFi
  most of the time.
- Offline periods of minutes to hours are normal. The sync strategy already
  accommodates this (see `ARCHITECTURE.md` § 6).
- Do **not** block any UI on a network call. Always read from Room.

---

## 7. Power and thermals

- Long sessions (8-hour shifts) with continuous Telecom + location + sync
  will drain the 7040 mAh in ~6 hours of screen-on.
- Recommend the tablet be plugged in during shifts. Add a "Plug in your
  tablet" hint in Settings if battery < 20% and no charger connected.
- Snapdragon 695 throttles under sustained load. Keep background work
  (sync, notifications) on short bursts; WorkManager's `KEEP` policy is
  already set to avoid duplicate jobs.

---

## 8. Emulator setup for development

AVD config that mimics Tab A9+:

```
Device:    11.0" Tablet
Resolution: 1920 × 1200
Density:   240 dpi (closest standard bucket to actual ~206)
API level: 33 (Android 13)
Target:    Google APIs (for telephony stubs)
```

Emulator loopback for local backend: use `http://10.0.2.2:3000/api/`
(uncomment the line in `build.gradle.kts` debug block).

⚠️ The emulator **cannot place real cellular calls**. For Telecom
development, either:
- Run on a physical Tab A9+ 5G with a SIM, or
- Mock the Telecom layer behind an interface so unit tests can verify
  the state machine without the hardware.

---

## 9. Known pitfalls on this hardware

| Issue | Mitigation |
|---|---|
| LCD makes pure-black backgrounds look washed out | Use Material 3 dark surfaces (`surfaceContainerHighest`), not `#000000`. |
| 90 Hz refresh on a mid-tier SoC → occasional jank | Avoid complex Compose recomposition trees on hot paths (call timer, live search). Use `remember` + `derivedStateOf` religiously. |
| Speaker is mono and mid-bottom — muffled when tablet is flat | Remind agents to keep tablet slightly propped; ship with a simple icon hint. |
| Stylus NOT supported — don't design for it | Don't use drawing, handwriting, or precision-tap interactions. |
| Split-screen multi-window is supported by default in Android 13 | Either embrace it (test all screens in 50/50 split) or disable: `android:resizeableActivity="false"`. |

---

## 10. Summary checklist for every new screen

- [ ] Works in landscape at 1920×1200.
- [ ] Uses two-pane layout when `widthSizeClass == Expanded` and it makes
      semantic sense (list + detail).
- [ ] All interactive elements ≥ 56 dp.
- [ ] Text ≥ 16 sp body, ≥ 20 sp heading.
- [ ] No blocking network call on the main thread.
- [ ] Survives split-screen (or activity declares non-resizable).
- [ ] Tested on emulator with the AVD config in § 8.
