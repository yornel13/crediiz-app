# IMPLEMENTATION_STATUS

Snapshot of what works, what's half-built, and what's missing.
**Keep this file updated as the source of truth when handing off work.**

Last updated: **2026-04-24** (sync/refresh defects logged in KNOWN_ISSUES.md)

> ⚠️ **Open defects:** five sync/refresh issues identified during code
> review on 2026-04-24 are tracked in [`KNOWN_ISSUES.md`](./KNOWN_ISSUES.md)
> (KI-01 through KI-05). Three are 🔴 High severity. None are blocking
> Phase 0 or Phase 1, but they must be triaged before v1.0 release.

> 🔗 **Backend coordination (calls-core):** the `direction` field on
> `InteractionEntity` (Phase 3.5 Option B) is now wired end-to-end —
> `Interaction` schema accepts the field, `SyncInteractionDto` (NestJS
> + Kotlin) carries it, sync.service maps it through. Backend defaults
> missing values to `OUTBOUND` so older mobile clients keep working.
> Verified 2026-04-25: 11/11 NestJS test suites pass, Android compiles.

---

## 🎯 MVP (v1.0) scope — quick reference

Full definition in [`DEVELOPMENT_PLAN.md` § "MVP scope"](./DEVELOPMENT_PLAN.md#-mvp-scope-v10--locked).

| Block | v1.0? | Phases |
|---|---|---|
| **Product boundary** — agent-only app, admin uses web panel | 🔒 locked rule | See [`OVERVIEW.md` § 0](./OVERVIEW.md#0-product-boundary-locked-rule) |
| Auth + data hydration | ✅ in v1.0 | Phase 0 |
| Call flow UI (Pre-Call + client notes panel + Post-Call) | ✅ in v1.0 | Phase 1 (incl. 1.8 add manual note), Phase 2 |
| Native Telecom (default dialer) | ✅ in v1.0 | Phase 3.0 onboarding · 3.1 ConnectionService · 3.2 InCallService+UI · 3.3 state machine · 3.4 disconnect mapper · 3.5 missed-call log. See [`TELECOM_ARCHITECTURE.md`](./TELECOM_ARCHITECTURE.md) and [`DIALER_SETUP_GUIDE.md`](./DIALER_SETUP_GUIDE.md). |
| Auto-call | ✅ in v1.0 | Phase 4 |
| Sync status indicator | ✅ in v1.0 | Phase 4.5.3 (UX-4) |
| Tablet touch targets | ✅ in v1.0 | Phase 6.4 |
| Release hardening (R8, crash recovery, Crashlytics) | ✅ in v1.0 | Phase 7.4, 7.5, 7.6 |
| Release packaging (signing, icon, versioning, AAB) | ✅ in v1.0 | Phase 8.1–8.4 |
| Agent's personal activity view (today's call counts, answered/unanswered, avg duration) | ⏳ v1.1 | New Phase TBD — mobile consumes backend aggregation endpoint. See v1.1 notes. |
| Option C hub (stats strip, today's call log, overdue section) | ⏳ v1.1 | Phase 4.5.1, 4.5.2, 4.5.4 |
| Follow-up notifications | ⏳ v1.1 | Phase 5 |
| Queue productivity (filters, correction, quick notes) | ⏳ v1.1 | UX-3, 5, 6, 7 |
| Unit + integration tests | ⏳ v1.1 | Phase 7.1–7.3 |
| Two-pane + i18n | ⏳ v1.2 | Phase 6.1–6.3, 6.5 |
| MDM deployment | ⏳ v1.2 | Phase 8.5 |
| Power UX (end-of-shift, break mode, general notes, etc.) | ⏳ v1.2+ | UX-9 through UX-15 |

**Rollout context:** 4 agents at launch, scaling afterward. Auto-call
and disconnect-cause mapping are MVP because of that scale (a 1–2 agent
pilot could defer both).

---

## ✅ Done (works end-to-end)

| Feature | Files / packages | Notes |
|---|---|---|
| Login (email + password → JWT) | `presentation/login/`, `data/remote/api/AuthApi.kt`, `data/local/preferences/TokenStore.kt` | Encrypted DataStore; expiration check via `JwtDecoder`. |
| App entry + DI bootstrap | `CallsAgentApp.kt`, `MainActivity.kt`, `di/*` | Hilt + WorkManager config wired. |
| Home hub with bottom nav | `presentation/home/HomeScreen.kt` | 3 tabs: Clients / Agenda / Settings. |
| Clients tab (list + search) | `presentation/clients/` | Reads PENDING clients from Room. Local filter. FAB present but auto-call logic stubbed. |
| **Clients tab redesign — final shape** | `presentation/clients/`, `presentation/clients/components/`, `data/local/db/ClientDao.kt`, `data/repository/ClientRepositoryImpl.kt` | Two pills: **Pendientes** + **Recientes** (Interesados pill removed — leads live in Agenda). Recientes combines recent calls + active dismissals (deduped by clientId, dismissal wins). Auto-call FAB scoped to Pendientes only. View-aware Hero + EmptyState. |
| **Agenda — Sin agendar section** | `presentation/agenda/`, `data/local/db/ClientDao.kt` | Orphan INTERESTED clients (no pending follow-up) listed at the bottom of Agenda, oldest `assignedAt` first. Each row has a `⋯` overflow menu with "Descartar cliente". |
| **CallOutcome.SOLD ✨** | `common/enums/CallOutcome.kt`, `ui/theme/StatusColors.kt`, `data/repository/ClientRepositoryImpl.kt` (mapping), `presentation/autocall/AutoCallOrchestrator.kt`, `presentation/autocall/AutoCallSession.kt`, `presentation/autocall/SessionSummaryScreen.kt`, backend `clients.service.ts` + `call-outcome.enum.ts` | New 6th call outcome → maps to `ClientStatus.CONVERTED`. Terminal in auto-call (stops countdown like INTERESTED). Renders as "Sold" badge in Recientes. Backend deployed in commit `8b868b2`. |
| **Mobile dismissal feature (D2/D3)** | `data/local/entity/ClientDismissalEntity.kt`, `data/local/db/ClientDismissalDao.kt`, `data/repository/ClientDismissalRepositoryImpl.kt`, `domain/repository/ClientDismissalRepository.kt`, `data/remote/dto/SyncDto.kt`, `data/sync/SyncManager.kt`, `presentation/clients/components/DismissClientSheet.kt`, `presentation/clients/components/RecentDismissalCard.kt`, overflow menus in `ClientCard` (Pendientes) + `UnscheduledRow` (Agenda) | Local entity + DAO + repo + sync push. Sheet with 6 reason chips + free-form. Recientes renders dismissal card variant with "Deshacer descarte" button (24 h window). DB version bumped to 3. |
| **KI-03 In-call sync gate** | `data/sync/InCallGate.kt`, `data/sync/SyncManager.kt` | Pull half (`refreshServerState`) skipped while `CallManager.callState` is Dialing/Ringing/Active. Push half always runs. Recovery on call end via existing `triggerImmediateSync` from Post-Call. |
| **Phase 7.5 — Orphan-call recovery** | `data/local/entity/InteractionEntity.kt` (new `confirmedByAgent` column), `data/local/db/InteractionDao.kt`, `domain/repository/InteractionRepository.kt`, `data/repository/InteractionRepositoryImpl.kt`, `presentation/postcall/PostCallViewModel.kt`, `presentation/navigation/CallNavigationViewModel.kt`, `presentation/navigation/AppNavGraph.kt`, `presentation/postcall/PostCallScreen.kt` (RecoveryBanner) | DB v4. Interactions track `confirmedByAgent` (set true on Post-Call save). On app start `CallNavigationViewModel.scanForOrphan` finds the most recent unconfirmed interaction (≤ 24 h) and routes the agent to Post-Call with a "Recuperando llamada anterior" banner. Stale unconfirmed interactions older than 24 h auto-confirm so the prompt doesn't repeat forever. |
| Agenda tab (follow-ups grouped by date) | `presentation/agenda/` | Today / Tomorrow / This Week / Later groupings from Room. |
| Settings tab (account info + toggles + sync status) | `presentation/settings/` | Auto-advance toggle, force-sync button, sync status dashboard. |
| Room database (4 entities + DAOs) | `data/local/db/`, `data/local/entity/` | Client, Interaction, Note, FollowUp. |
| Repository pattern (domain ↔ data) | `domain/repository/`, `data/repository/` | Interfaces in domain, implementations in data. |
| Sync infrastructure | `data/sync/SyncManager.kt`, `SyncWorker.kt`, `SyncScheduler.kt`, `ConnectivityObserver.kt` | Mutex-guarded batch sync, WorkManager periodic, reconnect listener. |
| Auth interceptor | `data/remote/interceptor/` | Injects bearer token. |
| `CallOutcome` enum (5 values) | `common/enums/` | Matches backend enum exactly. |
| Navigation graph (top-level routes declared) | `presentation/navigation/AppNavGraph.kt` | `login`, `home` functional; call-flow routes declared but stubs. |
| **`ClientsApi.getAssigned()`** — Retrofit call | `data/remote/api/ClientsApi.kt:14` | `GET /clients/assigned`. Plan task **0.1**. |
| **`FollowUpsApi.getAgenda()`** — Retrofit call | `data/remote/api/FollowUpsApi.kt:14` | `GET /follow-ups/agenda`. Plan task **0.2**. |
| **`ClientRepositoryImpl.refreshAssigned()`** | `data/repository/ClientRepositoryImpl.kt:33` | Fetches + replaces Room. Plan task **0.3**. |
| **`FollowUpRepositoryImpl.refreshAgenda()`** | `data/repository/FollowUpRepositoryImpl.kt:51` | Fetches + replaces Room. Plan task **0.4**. |

---

## ⚠️ Partial (started, incomplete)

### Phase 0 — Login-time hydration ✅ DONE (6/6)

`LoginViewModel.hydrate()` fetches PENDING clients + agenda in parallel
after a successful auth. Two-stage progress hint in UI ("Signing in..."
→ "Loading your queue..."). On hydration failure (offline) the user
still proceeds to Home; `LoginHydrationState` flags the session and
`HomeScreen` shows a dismissable banner.

Files: `presentation/login/LoginViewModel.kt`, `presentation/login/LoginScreen.kt`,
`presentation/home/HomeScreen.kt`, `presentation/home/HomeViewModel.kt`,
`data/sync/LoginHydrationState.kt`.

### Remote API clients — fully implemented now

~~`ClientsApi` declared, not called from any ViewModel yet.~~ → **Implemented.**
~~`FollowUpsApi` declared, not called.~~ → **Implemented.**

Still pending: calling these APIs from the login flow (see Phase 0 above).

### Call flow screens — navigation declared, bodies empty

**Where:** `presentation/navigation/AppNavGraph.kt` lines ~57–59.

| Screen | Current state | Gap |
|---|---|---|
| `PreCallScreen` | Placeholder Composable, TODO comment. | Needs client info card, interaction history, "Call" button. |
| `InCallScreen` | Placeholder Composable. | Needs live timer, notes field, mute/speaker, Telecom hookup. |
| `PostCallScreen` | Placeholder Composable. | Needs outcome picker (5 options), note editor, follow-up form if Interested. |

### Auto-Call mode — FAB is wired but no engine

**Where:** `presentation/home/HomeScreen.kt` ~line 85.
`onStartAutoCall` callback chain exists; body is a comment. Need:

- Countdown overlay (5 seconds with "cancel" affordance).
- Iterator over PENDING clients ordered by `queueOrder`.
- Post-call auto-advance on outcomes `NO_ANSWER` / `BUSY`.
- Session summary when the queue ends.

### Remote API clients — all implemented; consumption partial

| Interface | Used? |
|---|---|
| `AuthApi` | ✅ consumed by `LoginViewModel`. |
| `SyncApi` | ✅ consumed by `SyncManager`. |
| `ClientsApi` | ✅ implemented. ⚠️ Not yet called from `LoginViewModel` (Phase 0 task 0.5). |
| `FollowUpsApi` | ✅ implemented. ⚠️ Not yet called from `LoginViewModel`. Also should be called post-sync. |

---

## ❌ Missing (spec says yes, code says nothing)

### Telecom / dialer integration

- No `InCallService` subclass.
- No `ConnectionService` subclass.
- No default-dialer registration flow (`ACTION_CHANGE_DEFAULT_DIALER`).
- No mapping from Android `DisconnectCause` → app `CallOutcome`.
- No foreground service for active-call tracking (permission declared but
  unused).
- **See [`TARGET_DEVICE.md` § 1](./TARGET_DEVICE.md#1-device-specs-reference)**:
  this whole area is moot if the fleet is the WiFi-only SKU — VoIP
  replaces it.

### Notes capture during call

- `NoteEntity` + `NoteRepository` exist, but never written from any
  call-related UI (because the UI doesn't exist yet).

### Follow-up local notifications

- `AlarmManager` integration not started.
- Spec: fire a local notification 5 minutes before `FollowUp.scheduledAt`.
- `POST_NOTIFICATIONS` permission declared but unused.

### Auto-advance countdown logic

- Post-call screen should auto-advance to next client on NO_ANSWER / BUSY
  after 5 s (when auto-advance toggle is on).

### Crash recovery for orphaned calls

- If the app is killed while a call is in progress, the resulting
  interaction has `callStartedAt` but no `callEndedAt`. On next launch we
  should detect and either:
  1. Offer the agent a "close this interaction" flow, or
  2. Mark it as disconnected with a sentinel outcome.

### Session summary screen

- After an auto-call session ends: show total calls, outcomes breakdown,
  elapsed time.

### Strings / i18n

- Most UI strings are hardcoded English in Composables. MVP-acceptable but
  the target agent persona is Spanish-speaking. Post-MVP: move to
  `res/values/strings.xml` and add `values-es/`.

### Tablet-specific layout polish

- No two-pane layouts yet (see [`TARGET_DEVICE.md` § 3](./TARGET_DEVICE.md#3-compose-window-size-classes)).
- Screens currently render single-column; wastes horizontal real estate on
  the Tab A9+ expanded width.

### ProGuard / R8 for release

- `isMinifyEnabled = false` on release. Enable before shipping. Add Moshi
  and Retrofit keep rules.

---

## 🧪 Testing coverage

- Unit tests: near-zero. `ExampleUnitTest.kt` and
  `ExampleInstrumentedTest.kt` are the default AS scaffolding.
- **Recommendation (not blocking MVP):** cover at minimum:
  1. `SyncManager` state transitions (PENDING → SYNCED, FAILED path).
  2. JWT expiration logic in `JwtDecoder`.
  3. The Post-Call save flow (once built) — write to Room, request sync,
     handle success/failure.

---

## Prioritized next actions

> **The authoritative, detailed roadmap lives in
> [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md).**
> That file breaks each step below into concrete sub-tasks with affected
> files and Definition of Done. The list here is a summary.

Ordered for continuous flow — each unblocks the next. The DEVELOPMENT_PLAN
reorders and expands this list based on a second pass of the spec.

1. **Hydrate Room from server on login** — `ClientsApi.getAssigned()` +
   `FollowUpsApi.getAgenda()` called right after successful login. (Plan
   Phase 0; was #3 here — promoted because it blocks #1 and #2 below.)
2. **Pre-Call Screen** (pure UI, no Telecom). Tap a client → info +
   history → "Call" button. (Plan Phase 1.)
3. **Post-Call Screen** skeleton (outcome picker + note editor + follow-up
   form when Interested). Testable without a real call via a debug button.
   (Plan Phase 2.)
4. **Confirm SKU (WiFi vs 5G)** with the product owner — blocks all
   Telecom work. See [`TARGET_DEVICE.md` § 1](./TARGET_DEVICE.md#1-device-specs-reference)
   and DEVELOPMENT_PLAN GATE-A.
5. **Call Engine (Telecom)** split into 4 sub-phases in the plan:
   default dialer registration → `InCallService` + In-Call Screen →
   state machine → disconnect-cause mapping. (Plan Phase 3.)
6. **Auto-Call engine** (countdown + iterator + summary). (Plan Phase 4.)
7. **Follow-up local notifications** with AlarmManager. (Plan Phase 5.)
8. **Tablet polish** — two-pane layouts, strings.xml, i18n. (Plan Phase 6.)
9. **Hardening** — tests, R8, crash recovery, Crashlytics. (Plan Phase 7.)

---

## How to update this file

After completing any task from the list:

1. Move the item from its current section to **Done**.
2. Add the files / packages that implement it.
3. Bump the "Last updated" date at the top.
4. If new gaps are discovered, add them under **Partial** or **Missing**.

This is the **fastest way to onboard the next agent** — keep it honest.
