# DEVELOPMENT_PLAN

Step-by-step roadmap to ship the MVP. Derived from:

- [`calls-core/docs/MOBILE_APP.md`](../../calls-core/docs/MOBILE_APP.md) — full product spec
- [`IMPLEMENTATION_STATUS.md`](./IMPLEMENTATION_STATUS.md) — current state
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — module boundaries
- [`TARGET_DEVICE.md`](./TARGET_DEVICE.md) — tablet constraints
- [`AGENT_UX_BACKLOG.md`](./AGENT_UX_BACKLOG.md) — power-user features + Option C hub layout
- [`KNOWN_ISSUES.md`](./KNOWN_ISSUES.md) — defects + architectural concerns pending review (must triage before v1.0)

**How to use this file**

1. Pick the lowest-numbered uncompleted task.
2. Do exactly its scope — no more, no less.
3. When all DoD items tick, check the task here and move the matching
   entry in `IMPLEMENTATION_STATUS.md` from Partial/Missing → Done.
4. Bump the "Last updated" in this file.

Last updated: **2026-04-24** (KNOWN_ISSUES.md added; sync defects pending triage)

---

## 🎯 MVP scope (v1.0) — locked

**Rollout plan:** 4 agents from day 1, scaling afterward. This drove the
inclusion of auto-call and disconnect-cause mapping below (a pilot of 1–2
agents could defer them; 4+ cannot).

**Product boundary (non-negotiable):** the mobile app is agent-only.
Admin / owner uses the web panel in `calls-core`. See
[`OVERVIEW.md` § 0](./OVERVIEW.md#0-product-boundary-locked-rule).
Consequence for MVP: **the mobile app only emits raw data in v1.0** —
no dashboards, no reports, no cross-agent views. Daily/weekly/monthly
reporting is delivered by the `calls-core` admin panel consuming the
synced interactions.

### MVP definition

> An agent in the field can authenticate, see their assigned queue, place
> real calls, record outcomes, and have that data reach the backend — while
> surviving intermittent connectivity.

Anything not directly enabling that sentence is **out of v1.0**.

### Must-have (blockers for ship)

| Phase | Why it's in |
|---|---|
| **0** Hydrate Room on login | Without this the app opens empty. |
| **1** Pre-Call screen (incl. client notes panel + add manual note) | Entry point to every call + fulfills user requirement for a per-client notes panel. |
| **2** Post-Call screen | Without this no outcome is captured → no product. |
| **GATE-A** SKU decision | Hard blocker for Phase 3. |
| **3.1** Default dialer registration | Required to place calls. |
| **3.2** `InCallService` + In-Call UI | Required during calls. |
| **3.3** Call state machine | Required to close the loop (call end → Post-Call). |
| **3.4** Disconnect-cause → outcome mapping | At 4 agents × ~100 calls/day, manual miscategorization is ~10–15% — unacceptable for CRM data quality. |
| **4** Auto-call orchestrator | 400+ calls/day across the team with manual tap-to-next = product abandonment. |
| **4.5.3 / UX-4** Sync status indicator | Field connectivity is intermittent; agents must see pending-sync state. Promoted from Phase 4.5 because it's ~0.5 day of work and a field-ops safety net. |
| **6.4** Touch targets for Tab A9+ | Hardware is fixed; cramped buttons = unusable. |
| **7.4** R8 / ProGuard on release | Required for release APK size + obfuscation. |
| **7.5** Crash recovery (orphaned call reconciliation) | Without this a crashed call leaves the app in an inconsistent state — user-visible bug. |
| **7.6** Crashlytics | Required to diagnose field crashes remotely. |
| **8.1** Signing config | Required to install on fleet tablets. |
| **8.2** App icon + splash | Required for a shippable build. |
| **8.3** Versioning strategy | Required for OTA / update tracking. |
| **8.4** Bundle deliverable (AAB/APK) | Required to hand off to the install process. |

### Deferred to v1.1

| Item | Why it can wait |
|---|---|
| **Mobile agent activity view** (basic personal stats: calls today, answered count, etc.) | Requested by user but explicitly deferred to v1.1 — v1.0 mobile only emits raw data. Admin panel (calls-core) delivers the real daily/weekly/monthly reports. |
| **4.5.1 / UX-1** Today's stats strip | Superseded by the "agent activity view" line above. Same feature, belongs to v1.1. |
| **4.5.2 / UX-2** Today's call log | Agent can find recent clients via search on the pending queue. |
| **4.5.4 / UX-8** Overdue follow-ups section | Agenda tab already lists them; just not prioritized visually. |
| **Phase 5** Follow-up local notifications | Agenda tab is functional without alarms; agents review it at start of shift. |
| **Phase 9** all UX-3, 5, 6, 7 items | Productivity polish; not blocking daily work. |

### Deferred to v1.2+

| Item | Why |
|---|---|
| **6.1, 6.2, 6.3, 6.5** Two-pane + i18n + layout review | Single-pane is usable on Tab A9+; i18n waits for GATE-B. |
| **7.1, 7.2, 7.3** Unit + integration tests | Crashlytics + manual QA covers v1.0 risk; tests added incrementally. |
| **7.7** Timber + release log stripping | Low risk; `Log.d` in release is cosmetic, not functional. |
| **8.5** MDM / fleet deployment | 4 agents = manual sideload is fine. Revisit at 10+ agents. |
| **Phase 9** UX-9 through UX-15 | Power-user features. |

### Estimated MVP effort

~4 weeks of focused engineering once GATE-A is resolved. Parallelizable:
Phase 0 + Phase 1 can run beside early Phase 3.1 scaffolding.

---

## Progress snapshot

| Phase | Status | Notes |
|---|---|---|
| Phase 0 | 🟡 **4/6 done** | API interfaces + repo refresh methods already exist. Missing: login wiring + offline fallback. |
| Phase 1 | ⚪ 0/7 | Pre-Call — not started. Nav route is a TODO stub. |
| Phase 2 | ⚪ 0/6 | Post-Call — not started. Nav route is a TODO stub. |
| Phase 3 | ⚪ 0/13 | Telecom — not started. Gated by GATE-A. |
| Phase 4 | ⚪ 0/6 | Auto-call — not started. |
| Phase 5 | ⚪ 0/6 | Follow-up notifications — not started. |
| Phase 6 | ⚪ 0/5 | Tablet polish — not started. |
| Phase 7 | ⚪ 0/7 | Hardening — not started. |
| Phase 8 | ⚪ 0/5 | Release readiness — not started. |
| Phase 4.5 | ⚪ 0/4 | Agent hub (Option C) — **v1.1**, except 4.5.3 (UX-4 sync indicator) which is **MVP**. |
| Phase 9 | ⚪ 0/11 | Agent power UX — full backlog, all **v1.1+**. See [`AGENT_UX_BACKLOG.md`](./AGENT_UX_BACKLOG.md). |

**Next immediate task:** complete Phase 0 (tasks 0.5 and 0.6) — it's 80% done
and finishing it unblocks Phase 1.

---

## Corrections made to the original 9-item list in `IMPLEMENTATION_STATUS.md`

The original priority list was a good first pass but had three issues, fixed here:

1. **Missing prerequisite**: hydrating Room from the server right after login
   was listed as step #3. It's actually a **blocker** for steps #1 and #2 —
   without it the Pre-Call Screen has nothing to display. Promoted to Phase 0.
2. **Post-Call before In-Call**: the original had In-Call + Telecom (§5)
   before Post-Call (§2). Post-Call is testable without a real call (navigate
   to it manually with a stub interaction), so it should come first. This
   also decouples Post-Call work from the SKU question (Phase 3).
3. **Telecom integration was one giant bullet**. Split into 4 sub-phases
   (default dialer registration → `InCallService` → state machine → disconnect
   mapping) because each is 1–2 days of work and has its own DoD.

Order in this plan: **Phase 0 → 1 → 2 → [decision gate on SKU] → 3 → 4 → 5 → 6 → 7 → 8**.

---

## 🚧 Decision gates (blockers before some phases)

| Gate | Blocks | Question |
|---|---|---|
| **GATE-A** | Phase 3 and everything after | Tab A9+ **WiFi-only (SM-X210)** or **5G (SM-X216)**? Determines Telecom native vs VoIP. See [`TARGET_DEVICE.md` § 1](./TARGET_DEVICE.md#1-device-specs-reference). |
| **GATE-B** | Phase 6 translations | Confirm Spanish as sole UI language, or bilingual ES + EN? |

Do NOT start work downstream of a gate until it's resolved.

---

## Phase 0 — Hydrate Room from server on login

**Why it's first:** the app currently has empty Room state on fresh install.
The Clients tab is blank until sync runs, which only triggers periodically or
manually. Login is the natural point to do a full refresh.

### Tasks

- [x] **0.1** Implement `ClientsApi.getAssigned()` Retrofit call.
  - ✅ `data/remote/api/ClientsApi.kt:14` — `@GET("clients/assigned")`
- [x] **0.2** Implement `FollowUpsApi.getAgenda()` Retrofit call.
  - ✅ `data/remote/api/FollowUpsApi.kt:14` — `@GET("follow-ups/agenda")`
- [x] **0.3** Add `ClientRepository.refreshFromServer()` (named `refreshAssigned()`).
  - ✅ `data/repository/ClientRepositoryImpl.kt:33` — calls API + `dao.replaceAll()`
- [x] **0.4** Add `FollowUpRepository.refreshFromServer()` (named `refreshAgenda()`).
  - ✅ `data/repository/FollowUpRepositoryImpl.kt:51` — calls API + `dao.replaceAgenda()`
- [ ] **0.5** Hook both calls into the login success path in
  `LoginViewModel` **before** navigating to Home. Show a loading state
  while fetching.
  - ⚠️ `LoginViewModel` does NOT call `refreshAssigned()` / `refreshAgenda()` after login.
- [ ] **0.6** Handle offline login: if credentials are valid but the fetch
  fails, still proceed to Home and surface a "data may be stale" banner.
  - ⚠️ No offline handling in LoginViewModel. No banner component.

### Definition of Done

- Fresh install → login → Clients tab shows the 12 seeded clients
  immediately (no 15-minute wait).
- Agenda tab shows follow-ups immediately if any exist for the agent.
- Airplane mode login with valid cached token → goes to Home with local
  data + stale banner.

### Spec references

- [`MOBILE_APP.md` § 8.3 Client List Refresh](../../calls-core/docs/MOBILE_APP.md)
- [`MOBILE_APP.md` § 5.1 Login Screen](../../calls-core/docs/MOBILE_APP.md)

---

## Phase 1 — Pre-Call Screen

**Why it's next:** it's pure UI over existing data. Unblocks the call flow
without any Telecom complexity.

### Tasks

- [ ] **1.1** Create `presentation/precall/PreCallScreen.kt` Composable.
  Layout per [`MOBILE_APP.md` § 5.5](../../calls-core/docs/MOBILE_APP.md#55-pre-call-screen):
  - Client info card (name, phone, extraData rendered as key/value pairs).
  - Call history section (previous `callAttempts`, `lastOutcome`, `lastNote`,
    `lastCalledAt`).
  - **Client notes panel** — full historical list of notes for this client
    (all `NoteType` values: `CALL`, `POST_CALL`, `MANUAL`, `FOLLOW_UP`),
    sorted newest first. Each entry: timestamp, type badge, content. See
    task **1.8** for the "add manual note" affordance.
  - Follow-up context banner (visible only when navigated from Agenda).
  - Two buttons in Phase 1: `Call`, `Back`. **`Skip` button is deferred
    to Phase 4** — see Phase 4 task 4.7 for rationale.
- [ ] **1.2** Create `presentation/precall/PreCallViewModel.kt`.
  - Injects `ClientRepository` and `InteractionRepository` (for history).
  - Exposes `StateFlow<PreCallUiState>`.
- [ ] **1.3** Update `AppNavGraph.kt`: replace the `precall/{clientId}` stub
  with the real Composable. Pass optional `followUpId` argument for the
  Agenda flow.
- [ ] **1.4** Wire tap on a ClientsScreen row → `navigate("precall/$clientId")`.
- [ ] **1.5** Wire tap on an Agenda entry → `navigate("precall/$clientId?followUpId=$id")`.
- [ ] **1.6** Tablet two-pane: when `widthSizeClass == Expanded`, render
  the Pre-Call as the **detail pane** next to the list. See
  [`TARGET_DEVICE.md` § 3](./TARGET_DEVICE.md#3-compose-window-size-classes).
- [ ] **1.7** "Call" button action: for now, just log and navigate to
  `incall/{clientId}` (the stub). Real Telecom wiring comes in Phase 3.
- [ ] **1.8** **Add manual note** affordance on Pre-Call (MVP requirement).
  - Button or FAB-in-section: `Add note`.
  - Opens a bottom-sheet / dialog with a text field.
  - Saves as `NoteEntity(clientId, type = MANUAL, interactionMobileSyncId = null)`.
  - Append-only — no edit, no delete (per `MVP_OVERVIEW.md` § 8, note
    editing is out of scope).
  - Notes appear immediately in the client notes panel (Flow-backed).
  - `PreCallViewModel` exposes `addManualNote(content: String)` that
    delegates to `NoteRepository.create(...)` and triggers an immediate
    sync attempt.

### Definition of Done

- Tapping any client in Clients tab opens Pre-Call with correct data.
- Tapping a follow-up in Agenda opens Pre-Call with the reason banner.
- Client notes panel shows all prior notes for this client, newest first,
  with type badge and timestamp.
- "Add note" button writes a `MANUAL` note to Room, appears at the top
  of the notes panel within one frame, and is queued for sync.
- On landscape Tab A9+: list + Pre-Call render side by side.
- Back button returns to the tab that launched it.

---

## Phase 2 — Post-Call Screen (skeleton, no Telecom)

**Why before Telecom:** Post-Call is testable with any hardcoded
interaction. Building it now lets us validate persistence + sync end-to-end
without dialing a real call.

### Tasks

- [ ] **2.1** Create `presentation/postcall/PostCallScreen.kt`. Layout per
  [`MOBILE_APP.md` § 5.7](../../calls-core/docs/MOBILE_APP.md#57-post-call-screen):
  - Call summary header (duration).
  - Editable note field (pre-filled from interaction's `note` if present).
  - Outcome selector (5 buttons, exactly one selectable).
  - Conditional follow-up form (visible only on `INTERESTED`).
    - Date picker (min: tomorrow if invalid, min: now otherwise).
    - Time picker.
    - Reason text field (required).
  - `Save & Next` button.
- [ ] **2.2** Create `PostCallViewModel.kt`.
  - Accepts `clientId` + `interactionMobileSyncId` as SavedStateHandle args.
  - On save:
    1. Update `InteractionEntity` with outcome + callEndedAt + duration.
    2. Insert `NoteEntity` with `type = POST_CALL`, link to interaction.
    3. If INTERESTED: insert `FollowUpEntity` with scheduled date/time.
    4. If coming from Agenda: mark the source follow-up `COMPLETED` and
       emit a `SyncCompletedFollowUpDto` entry on next sync.
    5. Call `SyncManager.requestImmediateSync()` (fire and forget).
    6. Emit navigation event to next client or back to Home.
- [ ] **2.3** Update `AppNavGraph.kt`: replace `postcall/{clientId}/{interactionId}`
  stub with real Composable.
- [ ] **2.4** Add a dev-only "Simulate call ended" button in Pre-Call
  Screen during development to test Post-Call flow without Telecom. Flag
  behind `BuildConfig.DEBUG`, remove before release.
- [ ] **2.5** Disconnect-cause-based outcome pre-selection logic
  (stubbed for now, real integration in Phase 3.4). Accept an optional
  `prefilledOutcome` nav arg.
- [ ] **2.6** "Save & Next" behavior:
  - If in auto-call mode → advance to next client (logic lives in Phase 4).
  - If manual mode → return to Pre-Call of next queued client, OR Home if
    queue is empty.

### Definition of Done

- Can simulate a call end, land on Post-Call, select outcome, save.
- InteractionEntity, NoteEntity, (optional) FollowUpEntity appear in Room
  with `syncStatus = PENDING`.
- After save, SyncManager runs, records flip to `SYNCED`, backend shows
  them via `GET /api/interactions` for that agent.
- Interested → scheduling a follow-up appears in the Agenda tab after save.

### Spec references

- [`MOBILE_APP.md` § 5.7 Post-Call Screen](../../calls-core/docs/MOBILE_APP.md#57-post-call-screen)
- [`MOBILE_APP.md` § 10 Interaction Data Lifecycle](../../calls-core/docs/MOBILE_APP.md)

---

## 🚧 Decision gate GATE-A — resolve before Phase 3

Confirm the Tab A9+ SKU. Everything below assumes **5G / native cellular**.
If WiFi-only, replace Phase 3 with a VoIP integration phase (not yet drafted
— propose Twilio Programmable Voice Android SDK as the default).

---

## Phase 3 — Call Engine (native Telecom)

Broken into 4 independent sub-phases, each has its own DoD.

### 3.1 Default Dialer Registration

- [ ] **3.1.1** Add a one-time onboarding prompt after login: "To place
  calls, Panama Calls must be set as the default dialer." Explain why.
- [ ] **3.1.2** Trigger the system intent on tap:
  ```kotlin
  Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
      putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
               packageName)
  }
  ```
- [ ] **3.1.3** Persist in DataStore whether the user has dismissed the
  prompt and whether the app is currently the default dialer
  (`TelecomManager.getDefaultDialerPackage()` check on startup).
- [ ] **3.1.4** Add the required manifest entries for
  `ConnectionService` + `InCallService` subclasses (stub subclasses are
  fine for this task; implementations land in 3.2).

**DoD:** Fresh install → login → onboarding prompt → system dialog → app
becomes default dialer. State persists across app restarts.

### 3.2 `InCallService` + custom In-Call Screen

- [ ] **3.2.1** Create `telecom/CallsInCallService.kt` extending
  `android.telecom.InCallService`. Overrides `onCallAdded` / `onCallRemoved`.
- [ ] **3.2.2** Create a singleton `CallManager` (in `data/call/`) that
  holds the active `Call` reference and emits state via `StateFlow`.
- [ ] **3.2.3** Create `presentation/incall/InCallScreen.kt`:
  - Live timer (ticks 1s, uses `rememberCoroutineScope`).
  - Client info at top.
  - Mute / Speaker buttons backed by `AudioState`.
  - "Notes" button opens a `Notes overlay` Composable.
  - "End Call" button triggers `Call.disconnect()`.
- [ ] **3.2.4** `InCallScreen` reads state from `CallManager` via Hilt.
- [ ] **3.2.5** Lock orientation to landscape on this screen (see
  [`TARGET_DEVICE.md` § 2](./TARGET_DEVICE.md#2-orientation-policy)).

**DoD:** Placing a real call from Pre-Call opens the custom In-Call UI.
Timer runs. Mute / speaker change the audio state. "End Call" terminates.

### 3.3 Call State Machine

- [ ] **3.3.1** Map `Call.STATE_DIALING / RINGING / ACTIVE / DISCONNECTED`
  to app-level `CallUiState` (sealed class).
- [ ] **3.3.2** On `STATE_ACTIVE` start the timer and enable Notes overlay.
- [ ] **3.3.3** On `STATE_DISCONNECTED` transition to Post-Call with the
  captured duration and disconnect cause.
- [ ] **3.3.4** Handle crash-recovery: on app launch, check for an
  interaction with `callStartedAt` but no `callEndedAt`, surface a
  "finish incomplete call" modal.

**DoD:** All five call states visible in logs during a real call. Post-Call
arrives with correct duration.

### 3.4 Disconnect Cause → Outcome Mapping

- [ ] **3.4.1** Create `common/util/DisconnectCauseMapper.kt` with the
  table from [`MOBILE_APP.md` § 7.3](../../calls-core/docs/MOBILE_APP.md).
- [ ] **3.4.2** Pass the mapped outcome as `prefilledOutcome` nav arg to
  Post-Call.
- [ ] **3.4.3** Store the raw `DisconnectCause` on the InteractionEntity
  for later analysis.

**DoD:** Reject a call → Post-Call opens with NO_ANSWER pre-selected.
Busy signal → BUSY pre-selected. Agent can still change it.

---

## Phase 4 — Auto-Call Engine

- [ ] **4.1** Create `presentation/autocall/AutoCallOrchestrator.kt` (VM
  scoped to the Home graph). Holds the session queue + cursor.
- [ ] **4.2** Entry points: Clients FAB, Agenda today-FAB.
- [ ] **4.3** 5-second countdown overlay after NO_ANSWER/BUSY when
  auto-advance is ON (read from DataStore via Settings).
- [ ] **4.4** "Exit Auto-Call" button on Pre-Call and Post-Call screens.
- [ ] **4.5** Session summary screen when the queue is empty
  ([`MOBILE_APP.md` § 12](../../calls-core/docs/MOBILE_APP.md)).
- [ ] **4.6** Respect the `sourceTab` context so returning to the queue
  goes back to Clients or Agenda correctly.
- [ ] **4.7** **Add `Skip` button to Pre-Call Screen** *(carry-over from
  Phase 1 — explicitly deferred 2026-04-24)*.
  - Semantics: advance the auto-call cursor to the next pending client
    **without placing a call**. Increments a local "skipped today"
    counter on the orchestrator (no backend field, no `ClientStatus`
    change).
  - Visible only when Pre-Call was opened in **auto-call mode** (i.e.,
    `AutoCallOrchestrator` has an active queue). In manual mode the
    button stays hidden — `Back` covers that case.
  - **Reason for deferral:** in Phase 1 there is no auto-call cursor,
    so `Skip` would either duplicate `Back` (UX noise) or require
    premature auto-call infrastructure. It only earns its place in the
    UI once Phase 4 lands.
  - When working on Phase 4: confirm with user the exact behavior
    above (e.g., should `Skip` count toward the daily skip cap if there
    is one? Should it surface in the session summary?).

**DoD:** Start auto-call from Clients FAB → dial → post-call → 5s
countdown → next client → … → empty queue → summary.

---

## Phase 4.5 — Agent hub (Option C)

**MVP split:** only **4.5.3 (UX-4 sync indicator)** is in v1.0. The other
three tasks ship in v1.1 as part of the Option C hub layout.

**Why the split:** field agents need to see sync state immediately
(~0.5 day to implement, reuses existing `SyncManager` state). The other
three surfaces (stats, call log, overdue) are motivational/ergonomic
polish — the app is usable without them.

**Design source of truth:** [`AGENT_UX_BACKLOG.md` § 1](./AGENT_UX_BACKLOG.md#1-navigation-decision--option-c-locked-in).

### Tasks

- [ ] **4.5.1 — UX-1 Today's stats strip** *(v1.1)*
  - Add `InteractionDao.countByAgentAndDateRange(...)` and
    `countByOutcomeAndDateRange(...)`.
  - Expose `StateFlow<DailyStats>` from `ClientsViewModel`.
  - Render as a horizontal chip strip at the top of `ClientsScreen`.
- [ ] **4.5.2 — UX-2 Today's call log section** *(v1.1)*
  - Add `InteractionDao.streamTodayByAgent(agentId, startOfDay, endOfDay)`
    returning `Flow<List<InteractionWithClient>>`.
  - Collapsed section at the bottom of `ClientsScreen`; tap row → Pre-Call.
- [ ] **4.5.3 — UX-4 Sync status indicator** *(MVP — v1.0)* 🔴
  - Top-bar chip on `ClientsScreen` reading from `SyncManager` state
    (pending count + last sync timestamp). Tap → existing Settings
    sync dashboard.
  - Green/amber/red visual states per `AGENT_UX_BACKLOG.md` UX-4.
  - **Schedule:** implement alongside Phase 3 or right after — it has no
    dependency on Telecom, just on `SyncManager` which already exists.
- [ ] **4.5.4 — UX-8 Overdue follow-ups section** *(v1.1)*
  - Add `FollowUpDao.streamOverdueByAgent(agentId, now)`.
  - New "Overdue" section at the top of `ClientsScreen` (above
    pending queue) in error color.
  - Tap → Pre-Call with follow-up banner (reuse Agenda flow).

### Definition of Done

- Clients tab matches the Option C layout in `AGENT_UX_BACKLOG.md` § 1.
- Stats strip, overdue, pending queue, and "called today" render in order.
- All four surfaces update reactively as Room changes — no manual refresh.
- Airplane mode: stats strip still renders (Room-backed), sync indicator
  flips to amber/red, pending count grows.

---

## Phase 5 — Follow-Up Local Notifications

- [ ] **5.1** Create `notifications/FollowUpAlarmScheduler.kt` that uses
  `AlarmManager.setExactAndAllowWhileIdle`.
- [ ] **5.2** Schedule an alarm at `scheduledAt - 5 minutes` whenever a
  FollowUp is inserted.
- [ ] **5.3** Cancel the alarm when the FollowUp's status flips to
  COMPLETED or CANCELLED (including remotely-cancelled after sync refresh).
- [ ] **5.4** Create `notifications/FollowUpAlarmReceiver.kt` that posts
  a Notification with a deep-link to the Agenda entry.
- [ ] **5.5** Runtime request for `POST_NOTIFICATIONS` permission on first
  follow-up creation (Android 13+).
- [ ] **5.6** Handle `SCHEDULE_EXACT_ALARM` permission (required on
  Android 12+ for `setExactAndAllowWhileIdle`). Fallback to inexact alarms
  if denied.

**DoD:** Create a follow-up scheduled 6 minutes from now. 1 minute later
a notification fires. Tap it → app opens on the Agenda entry.

---

## Phase 6 — Tablet Polish

**MVP split:** only `6.4` (touch targets) is **required for v1.0**.
Everything else ships post-MVP.

### v1.0 must-have

- [ ] **6.4** Review touch target sizes per [`TARGET_DEVICE.md` § 4](./TARGET_DEVICE.md#4-touch-targets-and-typography). 🔴 MVP

### Post-MVP (v1.1+)

- [ ] **6.1** Two-pane layouts for Clients + Agenda tabs (master-detail
  via `WindowSizeClass`). See [`TARGET_DEVICE.md` § 3](./TARGET_DEVICE.md#3-compose-window-size-classes). *(v1.2)*
- [ ] **6.2** Extract all user-facing strings from Composables into
  `res/values/strings.xml`. *(v1.2)*
- [ ] **6.3** (Pending GATE-B) Add `res/values-es/strings.xml` and make
  Spanish the default language. *(v1.2, blocked by GATE-B)*
- [ ] **6.5** Review all screens in Android Studio Layout Validation at
  `11" Tablet, landscape`. *(v1.2)*

**DoD:** Running the debug APK on a Tab A9+ shows master-detail on every
main tab. All strings localizable. No cramped buttons.

---

## Phase 7 — Hardening

**MVP split:** `7.4`, `7.5`, `7.6` are **required for v1.0**.
`7.1`, `7.2`, `7.3`, `7.7` ship post-MVP.

> ⚠️ **Pending triage from `KNOWN_ISSUES.md`** — five defects (KI-01 to
> KI-05) found in the sync/refresh layer must be classified into v1.0
> or v1.1 **before** declaring Phase 7 complete. Three are 🔴 High and
> involve silent data loss or race conditions; KI-05 needs a 5-minute
> verification first. Track the triage decision here once made.

### v1.0 must-have

- [ ] **7.4** Enable R8 / ProGuard on `release` (`isMinifyEnabled = true`).
  Add keep rules for Moshi, Retrofit, Room. 🔴 MVP
- [ ] **7.5** Crash recovery: detect orphaned calls on launch, surface
  reconciliation UI. 🔴 MVP
- [ ] **7.6** Add Firebase Crashlytics (or equivalent) with a non-PII
  stacktrace-only config. 🔴 MVP — required to diagnose field crashes.

### Post-MVP (v1.1+)

- [ ] **7.1** Unit tests for `SyncManager` (PENDING → SYNCED paths,
  failure retry, mutex correctness). *(v1.1)*
- [ ] **7.2** Unit tests for `JwtDecoder` and `DisconnectCauseMapper`. *(v1.1)*
- [ ] **7.3** Integration test: Post-Call save writes 3 rows and flips
  them to SYNCED after sync (uses Room in-memory DB + MockWebServer). *(v1.1)*
- [ ] **7.7** Remove all `Log.d` in release builds (Timber with release
  tree that no-ops). *(v1.1)*

**DoD:** `./gradlew test connectedAndroidTest` passes. Release APK is
obfuscated and runs through the full happy path on a device.

---

## Phase 9 — Agent power UX (full backlog)

**Why last:** these are productivity features that assume the agent is
already functional on the device. They refine the experience for
high-volume daily use; none block shipping a working MVP.

**Source:** [`AGENT_UX_BACKLOG.md`](./AGENT_UX_BACKLOG.md). Each item
below links to its full spec in that doc.

### High priority (v1.1)

- [ ] **9.1 — UX-3** Last-outcome correction window (2-min edit). ⚠️ Blocked on backend semantics — see `AGENT_UX_BACKLOG.md` § 4.
- [ ] **9.2 — UX-5** Filters + sort on pending queue (persisted per agent).
- [ ] **9.3 — UX-6** "Save & Pick next" manual advance on Post-Call.
- [ ] **9.4 — UX-7** Quick-note chips in Post-Call.

### Medium priority (v1.2)

- [ ] **9.5 — UX-9** End-of-shift summary + CSV share.
- [ ] **9.6 — UX-10** Pause / On-break mode (DataStore + global banner).
- [ ] **9.7 — UX-11** Persistent client notes (`NoteType.GENERAL`). ⚠️ Blocked on product scope.
- [ ] **9.8 — UX-12** Duplicate phone detection.

### Post-v1 / nice to have

- [ ] **9.9 — UX-13** Global search (clients + follow-ups + interactions).
- [ ] **9.10 — UX-14** Dark mode with validated contrast.
- [ ] **9.11 — UX-15** Manual dial shortcut. ⚠️ Blocked on backend `dialedPhone` field.

### Definition of Done (per item)

See the matching `UX-*` block in `AGENT_UX_BACKLOG.md` — each has its own
DoD. Do not mark `9.x` done here until the corresponding backlog entry
is also checked.

---

## Phase 8 — Release readiness (optional for MVP, required for prod)

- [ ] **8.1** Signing config for release (upload key, Play App Signing).
- [ ] **8.2** App icon + splash screen (not default).
- [ ] **8.3** Versioning strategy (`versionCode`, `versionName`) from git tags.
- [ ] **8.4** Bundle deliverable: `./gradlew bundleRelease` → AAB for
  Play, or signed APK for direct install on fleet tablets.
- [ ] **8.5** MDM / deployment strategy for rolling out updates to
  the agent fleet.

---

## Dependency graph (visual)

```
Phase 0 ─► Phase 1 ─► Phase 2 ─────► [GATE-A] ─► Phase 3.1
                                                     │
                                                     ▼
                                                 Phase 3.2
                                                     │
                                                     ▼
                                                 Phase 3.3 ◄── (Phase 2 feedback)
                                                     │
                                                     ▼
                                                 Phase 3.4
                                                     │
                                                     ▼
                                                 Phase 4 ─► Phase 4.5 ─► Phase 5
                                                                            │
                                                                            ▼
                                                                       Phase 6 ─► Phase 7 ─► Phase 8
                                                                                                │
                                                                                                ▼
                                                                                          Phase 9 (UX backlog)
```

Phases 5, 6, 7 can be parallelized once Phase 4.5 is in.
Phase 9 items are independent of each other and can ship incrementally.

---

## MVP checklist (v1.0 — 4-agent rollout)

Ordered implementation sequence to reach v1.0. **Do not skip items in
this list** — it is the locked MVP scope for the initial 4-agent rollout.

### Sprint 1 (~1.5 weeks)

1. **Phase 0** (0.5 + 0.6) — finish login-time hydration.
2. **Phase 1** — Pre-Call screen.
3. **Phase 2** — Post-Call screen (with the Phase 2.4 "Simulate call ended"
   dev button enabled to test end-to-end before Telecom exists).

End of Sprint 1: full happy-path testable without a real call.

### 🚧 GATE-A resolution

Confirm SKU. Do not start Sprint 2 until resolved.

### Sprint 2 (~1.5–2 weeks)

4. **Phase 3.1** — default dialer registration.
5. **Phase 3.2** — `InCallService` + In-Call UI.
6. **Phase 3.3** — call state machine.
7. **Phase 3.4** — disconnect-cause → outcome mapping.
8. **Phase 4.5.3** (UX-4) — sync status indicator. Can parallelize with
   any of the above; no dependency on Telecom.

End of Sprint 2: real calls placed, outcomes auto-pre-selected, agent
sees sync state.

### Sprint 3 (~1 week)

9. **Phase 4** — auto-call orchestrator.
10. **Phase 6.4** — tablet touch targets review.
11. **Phase 7.4** — R8 + ProGuard.
12. **Phase 7.5** — crash recovery.
13. **Phase 7.6** — Crashlytics.
14. **Phase 8.1–8.4** — signing, icon, versioning, bundle.

End of Sprint 3: **v1.0 shippable**.

### Explicitly excluded from v1.0 (see `AGENT_UX_BACKLOG.md` for full spec)

- Phase 5 (local notifications) — Agenda tab is usable without alarms.
- Phase 4.5.1 / 4.5.2 / 4.5.4 (stats, call log, overdue sections).
- Phase 6.1 / 6.2 / 6.3 / 6.5 (two-pane, i18n, layout review).
- Phase 7.1 / 7.2 / 7.3 / 7.7 (unit + integration tests, Timber).
- Phase 8.5 (MDM — manual sideload is fine for 4 agents).
- Phase 9 entire (all 11 UX-power items).

### Post-v1.0 prioritization (proposed v1.1)

Once v1.0 is in field use for ≥1 week, prioritize based on actual agent
feedback. Suggested default order if no surprises:

1. Phase 4.5.1 / 4.5.2 / 4.5.4 (complete Option C hub).
2. Phase 5 (notifications).
3. UX-3 (correction window — if agents report miscategorization).
4. UX-5, UX-6, UX-7 (queue productivity).
5. Phase 7.1–7.3 (test coverage to reduce regression risk as scope grows).
