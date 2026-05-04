# TELECOM_ARCHITECTURE — `calls-agends` as a Native Dialer

> **STATUS: SUPERSEDED — 2026-05-03**
>
> This architecture is being **replaced** by a SIP/VoIP-only stack
> based on the Linphone SDK, registered against Voselia's SBC.
>
> **The decision** (Option A — Total Replacement):
>
> - Calls migrate from cellular PSTN to **SIP outbound** over the
>   internet. The SIM card is no longer the call carrier for the
>   agent workflow.
> - Once SIP is live, **the app no longer needs `RoleManager.ROLE_DIALER`**.
>   Default-dialer status only matters for intercepting PSTN traffic;
>   if there is no PSTN traffic, there is nothing to intercept.
> - `ConnectionService` and `InCallService` are deleted. The custom
>   in-call UI (`InCallActivity` + `InCallScreen`) and the persistence
>   layer (Interaction, Note, MissedCall, Sync) are **kept** — they
>   are call-engine-agnostic.
>
> **Why this doc still exists**: it documents the historical Telecom
> design and the rationale for incoming-call Option B. Both are
> useful as reference if the product ever needs PSTN interception
> again (e.g., a hybrid SIP+PSTN deployment).
>
> **For the active migration plan, see**:
>
> - [`SIP_MIGRATION_PLAN.md`](./SIP_MIGRATION_PLAN.md) — phased work plan
> - [`SIP_VOSELIA_CONFIG.md`](./SIP_VOSELIA_CONFIG.md) — SBC parameters
>
> **Sections that no longer apply to v1.x and beyond:**
>
> - §1.1 "We are the default dialer" — false from the SIP cutover onward.
> - §3.1 `CallsConnectionService` — file deleted in the migration.
> - §3.2 `CallsInCallService` — file deleted in the migration.
> - §3.3 `CallManager` — refactored to wrap `LinphoneCoreManager`
>   instead of `TelecomManager.placeCall()`. Persistence logic is
>   preserved.
> - §3.4 End-to-end call flow — replaced by the SIP flow in the
>   migration plan.
> - §4.4–4.5 Dialer-role intent filters and `RoleManager` flow —
>   removed in the migration.
> - §5 Audio routing — re-implemented on top of `AudioManager` +
>   Linphone audio router.
> - §6.2 Incoming-call Option B — **out of scope** for the SIP MVP.
>   Inbound calls are explicitly excluded; no SIP REGISTER persistence
>   between sessions, no PSTN interception.
>
> Sections that **remain valid**:
>
> - §1.3 Operational context (hands-free, hardware, volume, fleet size).
> - §6.5 Battery optimization (Samsung One UI is still aggressive — the
>   foreground service for SIP audio still needs the same grant).
> - §6.6 Role revocation handling — re-purposed to detect missing SIP
>   permissions instead of missing dialer role.
> - §6.7 Crash recovery — the orphan-call detection logic still applies.

---

**Original status (kept for history):**
**Status:** locked architectural reference for the call-flow side of v1.0.
Read before touching anything under `app/src/main/java/.../telecom/` or
related manifest entries.

Last updated: **2026-04-25** (initial; Option B locked for incoming calls).
Superseded **2026-05-03** by the SIP/Linphone migration.

---

## 1. Product foundation — what this app **is**

### 1.1 We are the default dialer

`calls-agends` becomes the **default dialer** on the corporate Tab A9+
once installed and onboarded. We are not "an app that uses the dialer"
— we **are** the dialer.

Concrete consequences:

- The agent taps **Call** on `PreCallScreen` → we invoke
  `TelecomManager.placeCall(...)` directly. No redirect, no system UI.
- The in-call screen is **our own Compose UI** (`InCallActivity`), not
  the system's.
- All telephony events for the SIM (placed, ended, disconnect cause,
  audio routing) flow through our `ConnectionService` + `InCallService`.
- The OEM dialer (Samsung Phone) is **dormant** while our role is held.
  It only resurfaces if the user revokes our role from Settings.

### 1.2 Manual trigger, system-level automation

Unlike systems that automate calls end-to-end (e.g., the `octopulse`
auto-calls module that schedules with `AlarmManager`), `calls-agends`
**always requires a human tap** to start a call. The "automation" is
strictly inside the OS: once the agent taps, we call `placeCall()`
directly without bouncing to the system dialer UI. From the agent's
perspective the call "just happens" — the screen flips to the in-call
view immediately.

| | Octopulse autocalls | calls-agends |
|---|---|---|
| Trigger | `AlarmManager` (no human) | Agent tap |
| Negotiation | None — direct `placeCall()` | None — direct `placeCall()` |
| In-call UI | Custom (`InCallActivity`) | Custom (`InCallActivity`) |
| Outcome capture | Automatic from `DisconnectCause` | Pre-filled from `DisconnectCause`, agent confirms |

### 1.3 Operational context

- **Hardware:** Samsung Galaxy Tab A9+ **5G SKU (SM-X216)** — corporate
  asset, not the agent's personal device. Cellular service via corporate
  SIM card.
- **Hands-free by default:** speaker forced on at call start. Agents
  type notes during the call. Wired headsets supported transparently
  by the system audio router.
- **Volume:** 50–200 calls/day per agent.
- **Fleet size at launch:** 4 agents, scaling afterward.
- **No personal use:** the device is single-purpose. Contacts app,
  messaging, and OEM dialer features are out of the user's daily
  workflow.

---

## 2. Distribution boundary — no Play Store

The APK ships **outside Google Play**, distributed via MDM or sideload
to corporate-owned devices. This unlocks practical freedoms but does
**not** dissolve OS-level constraints.

### 2.1 What "no Play Store" frees us from

| Constraint that would apply on Play Store | Status here |
|---|---|
| Phone & SMS permissions policy (prominent disclosure, business justification, screenshot of onboarding) | ❌ Does not apply |
| App Bundle (AAB) requirement | ❌ Does not apply — we ship signed APK |
| Per-release review | ❌ Does not apply |
| Restrictions on privileged permissions (`MANAGE_OWN_CALLS`, `READ_PHONE_NUMBERS`, etc.) | ❌ Marketplace doesn't gate them |
| Annual `targetSdk` minimum bump | ❌ We choose |
| Forced user-visible permission change disclosures | ❌ Handled via MDM internally |

### 2.2 What it does **not** free us from (still hard limits)

| OS-level constraint | Always in effect |
|---|---|
| Runtime permission grants (`CALL_PHONE`, `MODIFY_AUDIO_SETTINGS`, etc.) — interactive | ✅ Yes |
| `RoleManager.ROLE_DIALER` assignment requires user confirmation dialog | ✅ Yes — non-skippable |
| Signature-protected permissions (`MODIFY_PHONE_STATE`, `MANAGE_ROLE_HOLDERS`, `BIND_CARRIER_SERVICES`) | ✅ Reserved for system apps with OEM key |
| Background service limits, wake locks, doze | ✅ Yes — handled with foreground services |
| Carrier / IMS negotiation — VoLTE may downgrade to 2G voice for non-system dialers | ⚠️ Carrier-dependent; validate per SIM |
| User can revoke the dialer role at any time from Settings | ✅ Yes — must detect and re-prompt |

### 2.3 Practical implication

Our distribution model removes **policy** restrictions but every
**technical** constraint of Android Telecom remains. The mandatory
onboarding flow (Phase 3.0) is non-negotiable — there is no "skip
permissions" path, even for an enterprise app.

---

## 3. Module architecture

```
app/src/main/java/com/project/vortex/callsagent/
├── telecom/
│   ├── CallsConnectionService.kt   ← extends android.telecom.ConnectionService
│   ├── CallsInCallService.kt       ← extends android.telecom.InCallService
│   ├── CallManager.kt              ← @Singleton — active call state holder
│   ├── DefaultDialerHelper.kt      ← role acquisition + persistence
│   ├── DisconnectCauseMapper.kt    ← Telecom DisconnectCause → CallOutcome
│   └── model/
│       ├── CallUiState.kt          ← sealed class IDLE | DIALING | RINGING | ACTIVE | DISCONNECTED
│       └── CallEvent.kt            ← one-shot UI events
├── presentation/
│   ├── incall/
│   │   ├── InCallActivity.kt       ← lock-screen-friendly Activity host
│   │   ├── InCallScreen.kt         ← Compose UI: timer, live notes, mute/speaker, end
│   │   └── InCallViewModel.kt
│   └── onboarding/
│       ├── OnboardingActivity.kt   ← mandatory permission gate (Phase 3.0)
│       ├── OnboardingScreen.kt
│       └── OnboardingViewModel.kt
└── data/local/entity/
    └── MissedCallEntity.kt         ← incoming call rejection log (Option C)
```

### 3.1 `CallsConnectionService`

Extends `android.telecom.ConnectionService`. Its only responsibilities:

- `onCreateOutgoingConnection(...)` — return a `Connection` whose state
  transitions reflect the system's `Call.STATE_*` events. We use the
  default `setActive()` / `setDisconnected()` pattern; no custom audio
  pipeline.
- `onCreateIncomingConnection(...)` — **immediately reject**. Persist a
  `MissedCallEntity` with the caller number + timestamp. See
  [Option C](#62-incoming-calls--option-c-locked-for-v10).

Manifest:
```xml
<service
    android:name=".telecom.CallsConnectionService"
    android:exported="true"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.ConnectionService" />
    </intent-filter>
</service>
```

### 3.2 `CallsInCallService`

Extends `android.telecom.InCallService`. The system binds this when our
role is held and a call exists. Responsibilities:

- `onCallAdded(call)`:
  - Hand the `Call` reference to `CallManager`.
  - **Force speaker** via `setAudioRoute(CallAudioState.ROUTE_SPEAKER)`.
  - Launch `InCallActivity` with `FLAG_ACTIVITY_NEW_TASK | SINGLE_TOP`.
- `onCallRemoved(call)`:
  - Notify `CallManager.onCallEnded(disconnectCause)`.
  - `CallManager` persists `InteractionEntity` + (optional)
    `NoteEntity(type=CALL)`, then triggers navigation to `PostCallScreen`.

Manifest:
```xml
<service
    android:name=".telecom.CallsInCallService"
    android:exported="true"
    android:permission="android.permission.BIND_INCALL_SERVICE">
    <meta-data
        android:name="android.telecom.IN_CALL_SERVICE_UI"
        android:value="true" />
    <meta-data
        android:name="android.telecom.IN_CALL_SERVICE_RINGING"
        android:value="false" />
    <intent-filter>
        <action android:name="android.telecom.InCallService" />
    </intent-filter>
</service>
```

### 3.3 `CallManager`

`@Singleton` Hilt component. Holds:

- `currentCall: android.telecom.Call?`
- `currentClient: Client?` — set **before** `placeCall()` so that when
  `onCallAdded` fires we know which client this call belongs to.
- `callState: StateFlow<CallUiState>`
- `isMuted: StateFlow<Boolean>`, `isSpeakerOn: StateFlow<Boolean>`
- `liveNoteContent: MutableStateFlow<String>` — text the agent types
  during the call. Persisted as a `NoteEntity(type=CALL)` on call end
  if non-empty.

Public API:

- `startCall(client: Client)` — sets context + invokes `placeCall(uri, null)`.
- `setCall(call: Call, ctx: InCallService)` — called from `InCallService`.
- `mute(Boolean)`, `setSpeaker(Boolean)`, `disconnect()`.
- `onCallEnded(cause: DisconnectCause)` — internal: persists records,
  fires navigation event.

### 3.4 End-to-end call flow

```
[PreCallScreen]
   tap "Call"
        │
        ▼
   CallManager.startCall(client)
   ├─ stores currentClient
   └─ telecomManager.placeCall(tel:phone, null)
        │
        ▼ (Android Telecom framework)
   CallsConnectionService.onCreateOutgoingConnection → Connection
        │
        ▼
   CallsInCallService.onCallAdded(call)
   ├─ CallManager.setCall(call, this)
   ├─ setAudioRoute(SPEAKER)
   └─ Intent(InCallActivity).start()
        │
        ▼
[InCallActivity / InCallScreen]
   ├─ live timer (1s ticks from STATE_ACTIVE)
   ├─ live notes textarea (writes to CallManager.liveNoteContent)
   ├─ Mute / Speaker toggles
   └─ End Call button → call.disconnect()
        │
        ▼
   CallsInCallService.onCallRemoved(call)
   ├─ CallManager.onCallEnded(disconnectCause)
   ├─ persists InteractionEntity + NoteEntity(type=CALL)
   ├─ DisconnectCauseMapper computes prefilledOutcome
   └─ navigates to PostCallScreen
        │
        ▼
[PostCallScreen]  ← Phase 2
```

---

## 4. Permissions & manifest declarations

### 4.1 Runtime permissions (mandatory in onboarding)

| Permission | Reason | Critical |
|---|---|---|
| `CALL_PHONE` | Place outgoing calls via `placeCall()` | 🔴 Yes |
| `ANSWER_PHONE_CALLS` (API 26+) | Accept incoming calls from the in-app UI (Option B) | 🔴 Yes |
| `READ_PHONE_STATE` | Validate radio service before dialing | 🟡 Recommended |
| `MODIFY_AUDIO_SETTINGS` | Force speaker, manage audio routing | 🔴 Yes |
| `POST_NOTIFICATIONS` (API 33+) | Foreground service + future follow-up alarms | 🔴 Yes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Samsung One UI from killing `InCallService` | 🔴 Yes |

### 4.2 Permissions declared in manifest only (no runtime prompt)

| Permission | Reason |
|---|---|
| `MANAGE_OWN_CALLS` | Some OEMs require it for `PhoneAccount` registration |
| `FOREGROUND_SERVICE` | Generic foreground service capability |
| `FOREGROUND_SERVICE_PHONE_CALL` (API 34+) | Specific type required for telephony FG services |

### 4.3 Permissions explicitly NOT requested

- ❌ `RECORD_AUDIO` — only used by audio-analysis tools (e.g., octopulse
  ringback detection). We don't analyze audio.
- ❌ `MODIFY_PHONE_STATE`, `BIND_CARRIER_SERVICES` —
  `signature`-protected, not available to non-system apps.
- ❌ `READ_CALL_LOG`, `READ_CONTACTS` — out of scope; we don't expose
  device call history or contacts.

### 4.4 Activity intent-filters for dialer role eligibility

Required so the system recognizes us as a candidate dialer when the
user opens the role chooser. On `MainActivity`:

```xml
<activity android:name=".MainActivity" ...>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="tel" />
    </intent-filter>
</activity>
```

### 4.5 Role acquisition flow

```kotlin
// Android 10+ (API 29+) — preferred path
val roleManager = ctx.getSystemService(RoleManager::class.java)
if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
    !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    onboardingActivity.startActivityForResult(intent, REQUEST_DIALER)
}

// Android 6–9 fallback (we likely won't hit this — Tab A9+ ships Android 14)
val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
}
startActivity(intent)

// Last resort if the above are not navigable
val settings = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
startActivity(settings)
```

---

## 5. Audio routing

Tab A9+ is used hands-free. The `InCallService` forces speaker route on
every call:

```kotlin
override fun onCallAdded(call: Call) {
    super.onCallAdded(call)
    setAudioRoute(CallAudioState.ROUTE_SPEAKER)
    callManager.setCall(call, this)
    // ...
}
```

If the agent connects a wired headset, the system audio router takes
over and switches automatically — no extra logic. Bluetooth headsets
are not a target use case but would behave the same way.

The agent can manually toggle speaker off via the in-call UI; a manual
toggle is respected for the duration of that call. Next call resets to
speaker.

---

## 6. Decisions & limitations

### 6.1 One call at a time

Not designed to handle a second concurrent call. If an incoming call
arrives while another call (incoming or outgoing) is active,
`ConnectionService.onCreateIncomingConnection` returns a Connection
that immediately disconnects with `DisconnectCause.BUSY` and logs a
`MissedCallEntity(reason = BUSY_OTHER_CALL)`. We never expose
call-waiting, hold, or merge. Outbound calls cannot be initiated while
any call is active — `CallManager.startCall()` no-ops with a UI hint.

### 6.2 Incoming calls — Option B (locked for v1.0)

> **Updated 2026-04-25.** Originally scoped as Option C (silent reject +
> log) for v1.0 with promotion path to B in v1.1. Decision changed to
> ship Option B directly — the cost of losing client callbacks
> outweighs the ~1.5 day implementation delta.

When an incoming call hits the SIM, our `ConnectionService.onCreateIncomingConnection`
returns an **active `Connection`**. The call rings normally and our
`CallsInCallService.onCallAdded` launches a dedicated **incoming-call
UI** that:

1. **Looks up the caller** by phone number against the agent's
   assigned `ClientDao.findByPhone(phone)`. If matched, displays the
   client's name, status pill, and optionally last-call summary. If
   unmatched, shows just the number with an "Unknown number" label.
2. Presents two oversized buttons: **Accept** (green) and **Reject**
   (red). No other interactions during ring.
3. On **Accept** → transitions to the same `InCallActivity` UI as
   outbound calls (timer, live notes, mute/speaker, end), with a
   visible **"Incoming"** badge in the header so the agent knows the
   direction. Audio defaults to speaker.
4. On **Reject** → `call.disconnect()` + persist a
   `MissedCallEntity(phoneNumber, occurredAt, matchedClientId,
   reason = REJECTED)`.
5. On **timeout** (caller hangs up before agent answers) → persist
   `MissedCallEntity(reason = NOT_ANSWERED)`.

Answered incoming calls produce a regular `InteractionEntity` with a
new `direction: CallDirection` field set to `INBOUND`. They sync to
the backend the same way outbound interactions do.

**Why Option B over C:**

- Agents can answer client callbacks without needing to manually call
  back later — improves response time and conversion rate.
- Supervisor can reach the agent on the corporate SIM if needed.
- The cost (~1.5 day extra dev) is small compared to the operational
  value.

**Limitations vs. a "proper" dialer:**

- Still **one call at a time**. If an incoming arrives while the agent
  is on an active outgoing call, it is auto-rejected with reason
  `BUSY_OTHER_CALL` and logged as a missed call.
- Caller ID enrichment is limited to phone-number matching against
  assigned clients. We do not query device contacts or external
  directories.
- If the same phone matches multiple assigned clients (rare, but
  possible — see [KI-?? duplicate phone detection](./AGENT_UX_BACKLOG.md#ux-12-duplicate-phone-detection-)),
  the UI shows "Multiple matches" and lets the agent pick after
  accepting.

The `MissedCallEntity` log remains useful: it captures rejected and
not-answered events, surfaced as a banner in Clients tab so the agent
can return them when convenient.

### 6.3 Why Option D (forward to system UI) is impossible

Recurrent question. The Android Telecom model binds **exactly one**
`InCallService` at a time — the package holding `ROLE_DIALER`.
Implications:

- There is no API to transfer an active call from our app to another
  dialer. `TelecomManager` has no `transferCallTo(otherPackage)`.
- Programmatic role transfer requires `MANAGE_ROLE_HOLDERS` (signature-
  protected). Interactive transfer requires the user to confirm a
  system dialog — not feasible mid-ring.
- Dropping the call in our service does not rebind it elsewhere; it
  just terminates.
- Launching the OEM dialer with an Intent only opens its main screen,
  not an active-call screen — there is no active call from its
  perspective.

Conclusion: Option D doesn't exist in Android. The realistic spectrum
is { silent reject (A) | minimal own UI (B) | reject + log (C) }.
We pick C.

### 6.4 VoLTE / HD voice

Carrier-dependent. Some Panama operators may not allow VoLTE for
third-party dialers, downgrading to 2G voice. Calls still complete;
audio quality may be lower than the OEM dialer would deliver. Validate
on real corporate SIMs before declaring v1.0 acceptance.

### 6.5 Battery optimization

Samsung One UI is aggressive about killing background services. Without
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` granted (and ideally MDM
allowlist in production), the `InCallService` may be killed mid-call
on long shifts. Onboarding (Phase 3.0) requires this grant.

### 6.6 Role revocation by user

Settings → Apps → Default apps → Phone app — the user can remove our
role at any time. Detection:

- `MainActivity.onResume()` always re-checks
  `RoleManager.isRoleHeld(ROLE_DIALER)`.
- If false → redirect to `OnboardingActivity`. All other paths in the
  app are blocked until the role is restored.

This is also the catch-all for any permission revocation — onboarding
re-runs and gates re-entry.

### 6.7 Crash recovery

If the app crashes during an active call, the system call survives but
our UI loses state. On next cold start:

- `MainActivity` checks for an `InteractionEntity` with `callStartedAt`
  set but no `callEndedAt`.
- If found: prompt the agent — "Finish the call you were on?"
  → opens `PostCallScreen` to record the outcome manually.

Implemented in Phase 7.5.

---

## 7. Component dependency graph

```
                ┌──────────────────────┐
                │   OnboardingActivity │  Phase 3.0 — gate
                │   (mandatory)        │
                └──────────┬───────────┘
                           │ all granted
                           ▼
                ┌──────────────────────┐
                │     MainActivity     │
                │     + Compose nav    │
                └──────────┬───────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
      [Clients tab]   [Agenda tab]  [Settings tab]
            │
            ▼
       [PreCallScreen]
            │ tap Call
            ▼
       ┌────────────┐    placeCall()    ┌───────────────────┐
       │ CallManager├────────────────►  │ Telecom framework │
       └────────────┘                   └─────────┬─────────┘
                                                  │
                         ┌────────────────────────┼─────────────┐
                         ▼                        ▼             │
            ┌──────────────────────┐  ┌──────────────────────┐  │
            │ CallsConnectionSvc   │  │ CallsInCallService   │  │
            │ (Connection lifecycle)│  │ (UI binding + audio)│  │
            └──────────────────────┘  └──────────┬───────────┘  │
                                                 │              │
                                                 ▼              │
                                       [InCallActivity] ◄───────┘
                                                 │ call ends
                                                 ▼
                                       [PostCallScreen]  Phase 2
```

---

## 8. References

- Octopulse autocalls module — guide implementation for
  `ConnectionService` + `InCallService` patterns.
  Path: `/Users/yornel/projects/alcancia/octopulse/Octopulse/src/main/java/com/octolytics/octopulse/modules/autocalls/`.
- [`DEVELOPMENT_PLAN.md` § Phase 3](./DEVELOPMENT_PLAN.md) — task
  breakdown.
- [`DIALER_SETUP_GUIDE.md`](./DIALER_SETUP_GUIDE.md) — step-by-step
  implementation guide.
- [`KNOWN_ISSUES.md`](./KNOWN_ISSUES.md) — sync defects that intersect
  with telecom (esp. KI-03 — "no in-call guard for refresh").
- Android Telecom framework docs — https://developer.android.com/reference/android/telecom/package-summary
