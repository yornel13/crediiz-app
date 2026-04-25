# AGENT_UX_BACKLOG — Power-user features for high-volume agents

Tracking backlog of UX and productivity features **not** covered by the
MVP call-flow phases in [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md).

Target persona: agent doing **50–200 calls per day** on a Tab A9+, often
with intermittent connectivity, reopening the app many times during a shift.

Last updated: **2026-04-21** (MVP scope reconciled for 4-agent rollout)

> **MVP note:** For the initial 4-agent rollout, only **UX-4 (sync
> status indicator)** is in v1.0. Everything else in this file ships in
> v1.1 or later. See [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md#-mvp-scope-v10--locked)
> § "MVP scope" for the full cut.
>
> **Product boundary:** the Android app is agent-only. Admin uses the web
> panel in `calls-core`. Any dashboard / cross-agent / reporting feature
> that appears in this backlog is **for the agent's self-view only** —
> never for supervisor use. Supervisor-facing analytics belong to the
> web panel and are not tracked here. See
> [`OVERVIEW.md` § 0](./OVERVIEW.md#0-product-boundary-locked-rule).

---

## 1. Navigation decision — Option C (locked in)

The Clients tab evolves from "a list of pending clients" into the agent's
**work hub for the day**. It is the landing screen after login and contains
sectioned content instead of a flat list.

```
┌───────────────────────────────────────────────┐
│  Clients (tab)                                │
│  ┌─────────────────────────────────────────┐  │
│  │ [ Today's stats strip ]                 │  │  ← KPIs (item 1)
│  │  12 calls · 3 interested · 42% contact  │  │
│  └─────────────────────────────────────────┘  │
│                                               │
│  ── OVERDUE FOLLOW-UPS (2) ────────────       │  ← promoted (item 8)
│  [ overdue row ]                              │
│  [ overdue row ]                              │
│                                               │
│  ── PENDING QUEUE (48) ────────────────       │
│  [ search + filters ]                         │  ← items 5, 6
│  [ client row ]                               │
│  [ client row ]                               │
│                                               │
│  ── CALLED TODAY (12) ─────────────[expand]   │  ← item 2
│  [ collapsed; tap to expand call log ]        │
└───────────────────────────────────────────────┘
      Clients · Agenda · Settings    (bottom nav)
```

**Why Option C over A/B:**

- Keeps the current 3-tab structure (no nav churn for users already trained).
- Concentrates the high-frequency surfaces (queue, overdue, stats, today's
  work) into the screen the agent opens 100× per shift.
- Agenda tab stays dedicated to **future** follow-ups; Clients tab owns
  **today**.

**Rejected alternatives:**

- *Option A (new Dashboard tab)* — adds a tab the agent would bounce off
  immediately to reach the queue. Splits attention.
- *Option B (just a header on Clients)* — too narrow; no room for
  overdue + call log sections without crowding.

---

## 2. Backlog items (priority-ordered)

Legend:
`🔴` critical · `🟡` high · `🟢` medium · `🔵` nice to have
`[ ]` not started · `[~]` partial · `[x]` done

### 🟡 v1.1 — critical for scaling beyond 4 agents *(originally tagged 🔴 v1.0, demoted in MVP reconciliation)*

Rationale: these improve daily ergonomics but are not blocking the first
4-agent rollout. Revisit immediately after v1.0 lands.

#### UX-1. Today's stats strip *(top of Clients tab)* — **v1.1**

- [ ] Read-only KPIs computed from Room for the current local day:
  - Calls placed, interested count, no-answer count, rejected count,
    contact rate (contacted / dialed), follow-ups scheduled today.
- [ ] Data source: `InteractionDao` — add `countByAgentAndDateRange(...)`
  and `countByOutcomeAndDateRange(...)`.
- [ ] `StateFlow<DailyStats>` on `ClientsViewModel` (or a new
  `DailyStatsViewModel` if the Clients VM gets heavy).
- [ ] UI: horizontal strip of 3–4 chips with icon + number + label.
- [ ] Updates reactively as new interactions are inserted.

**DoD:** After placing 5 calls, strip shows `5 calls` without reopening
the app. Works offline (data from Room, not network).

#### UX-2. Today's call log *("Called today" section)* — **v1.1**

- [ ] New collapsed section at the bottom of Clients tab listing all
  interactions this agent recorded today, newest first.
- [ ] Each row: client name, time, outcome badge, note preview.
- [ ] Tap row → opens Pre-Call for that client (enables re-call).
- [ ] `InteractionDao.streamTodayByAgent(agentId, startOfDay, endOfDay)`
  returning a `Flow<List<InteractionWithClient>>`.

**DoD:** Agent can answer "whom did I call at 10am?" in under 3 seconds
without leaving the Clients tab.

#### UX-3. Last-outcome correction window — **v1.1**

- [ ] On Post-Call save, keep the interaction editable for **120 seconds**.
- [ ] Surface an "Edit last call" affordance (snackbar or chip near the
  stats strip) that reopens Post-Call in edit mode.
- [ ] Block edit once the record has synced OR the window has expired,
  whichever comes first.
- [ ] On edit save: update in place (do NOT create a new interaction).
  Backend dedupe is on `mobileSyncId`, so the existing `PATCH` semantics
  (or a second POST overriding the same id) must be confirmed with
  `calls-core`.

**⚠️ Open question:** does the backend accept a re-POST of the same
`mobileSyncId` with different fields, or do we need `PATCH /interactions/{mobileSyncId}`?
Resolve before starting.

**DoD:** Agent marks `NOT_INTERESTED` by mistake, within 2 min changes
it to `NO_ANSWER`, backend reflects the final state.

#### UX-4. Sync status visible everywhere — 🔴 **MVP (v1.0)** ⬅

**Only item in this file included in v1.0.** Field connectivity is
intermittent; agents need to see pending-sync state or they will lose
trust in the app. Cheap to implement (~0.5 day) using existing
`SyncManager` state.

- [ ] Persistent indicator in the top bar or stats strip:
  - Pending count (`syncStatus = PENDING` across all entities).
  - Last successful sync timestamp.
  - Tappable → opens the sync status dashboard already in Settings.
- [ ] Visual state: green (0 pending), amber (>0 pending, <5 min since
  sync), red (>15 min since sync OR last attempt failed).
- [ ] Reuse `SyncManager` state; don't create a second source of truth.

**DoD:** On airplane mode, agent sees the counter grow as they work.
Re-enabling network drops it to zero within the sync cycle.

### 🟡 High — v1.1

#### UX-5. Filters + sort on pending queue — **v1.1**

- [ ] Filter chips above the list: `Never called`, `Retry`, `Has notes`.
- [ ] Sort menu: `Name A-Z`, `Attempts asc/desc`, `Last called oldest`,
  `Queue order` (default, from backend).
- [ ] Optional: filter by `extraData.city` if present — needs backend
  schema confirmation on which keys are guaranteed.
- [ ] Persist last-used filter/sort per agent in DataStore.

**DoD:** Agent filters to "Never called", count header updates, state
survives app restart.

#### UX-6. "Next client" manual advance button — **v1.1**

- [ ] On Post-Call save, add a secondary button `Save & Pick next`
  that does NOT start auto-call but picks the next pending client and
  opens Pre-Call for them.
- [ ] Queue cursor lives in the same `AutoCallOrchestrator` (Phase 4),
  but `auto-advance-on-timer` is disabled.
- [ ] Works with filters applied: next = next item in the currently
  filtered+sorted list.

**DoD:** Agent can do 20 calls in a row with one tap between each, no
auto-countdown.

#### UX-7. Quick-note chips — **v1.1**

- [ ] Predefined note templates as tappable chips in Post-Call:
  - `Asked for callback later`
  - `Wrong number`
  - `Not the owner`
  - `Language barrier`
  - `Voicemail left`
- [ ] Tapping appends the template text to the note field (does not
  overwrite existing content).
- [ ] Templates live in a single `QuickNoteTemplates.kt` constant file.
  Localize when GATE-B resolves.

**DoD:** Post-Call can be saved in under 5 seconds for the top 3 common
scenarios.

#### UX-8. Overdue follow-ups promotion — **v1.1**

- [ ] `FollowUpDao.streamOverdueByAgent(agentId, now)` returning entries
  with `scheduledAt < now` and `status = PENDING`.
- [ ] Dedicated "Overdue" section in Clients tab (per Option C layout
  above), styled in error color with an attention icon.
- [ ] Also surface an "Overdue" pill in Agenda section headers if count > 0.
- [ ] Tap → Pre-Call for that client with the follow-up reason banner
  (same flow as Agenda tap).

**DoD:** Follow-up from yesterday at 10am not yet completed shows in
Clients tab top section with a red tag.

### 🟢 Medium — v1.2

#### UX-9. End-of-shift summary

- [ ] Trigger manually (Settings button "End my shift") — no automatic
  detection.
- [ ] Full-screen recap: calls, interested, follow-ups scheduled,
  tomorrow's agenda preview, best/worst time-of-day contact rate.
- [ ] Option to export as CSV share intent (for supervisors who want it
  by Slack/email — purely device-side, no server involvement).

**DoD:** Agent ends shift, sees one screen that summarizes the day.

#### UX-10. Pause / Do-Not-Disturb mode

- [ ] Toggle in Settings: `On break`.
- [ ] While on, auto-call orchestrator will not advance, and follow-up
  alarms are snoozed (they fire again 5 min after toggle off).
- [ ] Persist through app restart (DataStore).
- [ ] Displayed as a banner across the top of every screen while active.

**DoD:** Toggling "On break" prevents the next auto-call advance; toggling
off resumes normally.

#### UX-11. Persistent client notes *(append-only)*

- [ ] In Pre-Call, expose a "General notes" section separate from the
  per-call notes already in scope.
- [ ] Agent can add a new note without starting a call. Notes are
  `NoteEntity` with `type = GENERAL`.
- [ ] Keep append-only semantics — no edit, no delete from the device.

**⚠️ Scope note:** [`OVERVIEW.md` § 7](./OVERVIEW.md) excludes client
editing from MVP. This item only adds *notes*, not client field edits.
Confirm with product before implementing.

**DoD:** Agent adds "Prefers after 6pm" in Pre-Call, sees it listed on
the next Pre-Call open for the same client.

#### UX-12. Duplicate phone detection

- [ ] `ClientDao.findDuplicatesByPhone(agentId)` — groups assigned
  clients by normalized phone.
- [ ] Warning chip on Pre-Call if the selected client's phone matches
  another assigned client.
- [ ] Non-blocking — the agent can still proceed.

**DoD:** Two seeded clients with the same phone number show the warning.

### 🔵 Nice to have — backlog / post-v1

#### UX-13. Global search

- [ ] Single search box at the top of Home covering clients +
  follow-ups + today's interactions.
- [ ] Probably implemented as a top-bar action icon that opens a
  full-screen search surface.

#### UX-14. Dark mode

- [ ] Proper dark theme in `ui/theme/Theme.kt` tied to system setting.
- [ ] Validate all status colors (overdue red, sync-warn amber) have
  accessible contrast in both themes.

#### UX-15. Manual dial shortcut

- [ ] "Dial another number for this client" affordance in Pre-Call.
- [ ] Creates an `InteractionEntity` linked to the client but with a
  temporary `dialedPhone` override field.
- [ ] Needs backend field `dialedPhone` on interaction POST payload —
  coordinate with `calls-core`.

---

## 3. Data-layer changes implied by this backlog

Collecting them so Phase 7 tests can cover them:

| DAO / entity | New method / field | Used by |
|---|---|---|
| `InteractionDao` | `countByAgentAndDateRange`, `countByOutcomeAndDateRange` | UX-1 |
| `InteractionDao` | `streamTodayByAgent` | UX-2 |
| `InteractionEntity` | mutable window → editable until synced | UX-3 |
| `FollowUpDao` | `streamOverdueByAgent` | UX-8 |
| `NoteEntity` | new `NoteType.GENERAL` | UX-11 |
| `InteractionEntity` | optional `dialedPhone` | UX-15 |
| DataStore | `lastUsedFilter`, `lastUsedSort`, `onBreak` | UX-5, UX-10 |

---

## 4. Open questions blocking implementation

1. **UX-3:** Backend semantics for editing an existing interaction —
   re-POST same `mobileSyncId`, or PATCH endpoint? → ask `calls-core`.
2. **UX-5:** Which `extraData` keys are guaranteed present and safe to
   filter on (city, loanType, etc.)? → ask `calls-core`.
3. **UX-11:** Is adding `NoteType.GENERAL` (not tied to an interaction)
   acceptable for MVP scope? → ask product.
4. **UX-15:** Can the backend accept `dialedPhone` on interaction
   payload? → ask `calls-core`.

Do NOT start an item that depends on an unanswered question above.

---

## 5. Ordering vs. existing plan

After MVP reconciliation for the 4-agent rollout, only **UX-4** lives
inside v1.0. Everything else is post-MVP.

```
── MVP (v1.0) ─────────────────────────────────────────────┐
 Phase 0 → 1 → 2 → [GATE-A] → 3.1 → 3.2 → 3.3 → 3.4 → 4    │
                                             + UX-4 (anywhere in Sprint 2)
                                             + 6.4, 7.4, 7.5, 7.6, 8.1–8.4
└──────────────────────────────────────────────────────────┘
                              │
                              ▼
── v1.1 ───────────────────────────────────────────────────┐
 Phase 5 (notifications)                                   │
 UX-1, UX-2, UX-8 (complete Option C hub)                  │
 UX-3, UX-5, UX-6, UX-7 (queue productivity)               │
 Phase 7.1–7.3 (tests)                                     │
└──────────────────────────────────────────────────────────┘
                              │
                              ▼
── v1.2+ ──────────────────────────────────────────────────┐
 Phase 6.1–6.3, 6.5 (tablet polish + i18n after GATE-B)    │
 UX-9, UX-10, UX-11, UX-12                                 │
 UX-13, UX-14, UX-15                                       │
└──────────────────────────────────────────────────────────┘
```

See `DEVELOPMENT_PLAN.md` **"MVP checklist"** for the Sprint-by-Sprint
breakdown of v1.0, and Phase 9 for the post-MVP tracking list.
