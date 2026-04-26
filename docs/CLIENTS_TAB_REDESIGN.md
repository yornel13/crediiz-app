# CLIENTS_TAB_REDESIGN — Re-architecting how the agent sees their queue

**Status:** 📐 Design proposal — pending approval
**Last updated:** 2026-04-26
**Owner:** mobile (`calls-agends`) with one open item that requires
`calls-core` alignment (see § 7).
**Related:** [`CLIENT_DISMISSAL.md`](./CLIENT_DISMISSAL.md) — the
agent-initiated discard feature also surfaces in Recientes.

---

## 1. Why we are rewriting this screen

Today the Clients tab shows **only `status = PENDING`** clients assigned
to the agent. Everything else is invisible. Three concrete UX failures
fall out of that:

1. **Reciente recall (last-24h):** if the agent finishes a call and 5
   minutes later remembers a detail to add to the note, the client is
   already gone from the visible queue. There is no way to navigate
   back to them.
2. **Interesados follow-up:** an `INTERESTED` client is a warm lead the
   agent will keep working for days/weeks (re-call, schedule a
   follow-up, refine the note). Today they vanish from the Clients tab
   the moment the agent marks them — only the Agenda surfaces them
   again, and only via the scheduled follow-up.
3. **Re-assignment after reject:** when the admin re-assigns a
   previously-rejected client back to the agent (e.g. after a campaign
   reset or a manual override), the device should pick them up cleanly
   on the next refresh. Today this works for `PENDING` re-assignments
   but the agent has no view to confirm "this client is back in my
   list".

The fix is **not** "show everything everywhere" — it is to give the
agent a small, well-defined set of views, each with a clear mental
model, and design them with the same care as the rest of the app.

---

## 2. The new mental model — three views, one tab

The Clients tab becomes a **multi-view container** with a top-level
selector. The three views never overlap conceptually:

| View | What lives here | Sort | Stays here for |
|---|---|---|---|
| **Pendientes** | `status = PENDING`, ready to call | `queueOrder ASC` | Until the agent calls and marks an outcome that moves them out, the agent dismisses them (see [`CLIENT_DISMISSAL.md`](./CLIENT_DISMISSAL.md)), or admin reassigns away |
| **Recientes** | Clients called **OR** dismissed in the last 24 h | `max(lastCalledAt, dismissedAt) DESC` | A rolling 24 h window — auto-fades, no manual cleanup |
| **Interesados** | `status = INTERESTED`, indefinite | `lastCalledAt DESC` (most recent on top) | Until the agent converts them (`CONVERTED`), rejects them (`REJECTED`), dismisses them (`DISMISSED`), or admin reassigns away |

The agent's day naturally flows **left → right** across these:

```
Pendientes ─call→ Recientes ─if INTERESTED→ Interesados ─convert/reject→ (out)
                  ↑                          │
                  └── still visible 24h ─────┘
```

A client never appears in two views at the same time **except** that an
INTERESTED client called less than 24 h ago appears in both Recientes
**and** Interesados. That is intentional: Recientes is a time-windowed
recall surface; Interesados is a permanent lead pipeline.

---

## 3. Data sourcing — which Room query feeds which view

All three views are **pure local queries** over the existing `clients`
table. No new entities required, no new endpoints required (with one
exception, see § 7).

### 3.1 Pendientes (no change from today)

```kotlin
@Query("SELECT * FROM clients WHERE status = :status ORDER BY queueOrder ASC")
fun observeByStatus(status: ClientStatus): Flow<List<ClientEntity>>
// called with ClientStatus.PENDING
```

### 3.2 Recientes (new)

Recientes has **two sources** combined client-side:

1. Clients with a recent call: `lastCalledAt >= since`.
2. Clients with a recent dismissal: a row in `client_dismissals`
   with `dismissedAt >= since AND undone = false` (see
   [`CLIENT_DISMISSAL.md`](./CLIENT_DISMISSAL.md)).

```kotlin
// Source 1: clients with a recent call
@Query("""
  SELECT * FROM clients
  WHERE lastCalledAt IS NOT NULL
    AND lastCalledAt >= :since
  ORDER BY lastCalledAt DESC
""")
fun observeRecentlyCalled(since: Instant): Flow<List<ClientEntity>>

// Source 2: clients with an active recent dismissal
@Query("""
  SELECT c.* FROM clients c
  INNER JOIN client_dismissals d ON d.clientId = c.id
  WHERE d.dismissedAt >= :since
    AND d.undone = 0
  ORDER BY d.dismissedAt DESC
""")
fun observeRecentlyDismissed(since: Instant): Flow<List<ClientEntity>>
// called with Instant.now().minus(24, ChronoUnit.HOURS)
```

The ViewModel `combine`s both Flows, deduplicates by `clientId`
(more recent event wins), and produces a single
`Flow<List<RecentEntry>>` where `RecentEntry` is a sealed type:
`Called(client, outcome, timestamp)` or `Dismissed(client, reason, timestamp)`.

**Why not a JOIN over `interactions`?** The `clients` table already
holds `lastCalledAt` and `lastOutcome`, kept in sync by
`applyInteractionLocally(...)` and the server's
`updateClientOnInteraction(...)`. One table for source 1, one
narrow JOIN for source 2.

**Window source of truth:** the ViewModel computes `since` once when
the view is opened and subscribes once. We do **not** re-query on a
ticker — the next sync, the next outcome save, or a navigation back
into the screen naturally re-evaluates. A 24 h cutoff is forgiving
enough that staleness within a view session is irrelevant.

### 3.3 Interesados (new)

Same DAO method as Pendientes, called with `ClientStatus.INTERESTED`.
Already populated by the existing `refreshAssigned(INTERESTED)` sync
path. **Zero new sync work.**

```kotlin
clientRepository.observeAssigned(ClientStatus.INTERESTED)
```

---

## 4. Visual design — modern, cohesive, not another tab row

### 4.1 Top-level selector — segmented pill

We do **not** use `TabRow` / `ScrollableTabRow`. Reason: the bottom
nav already uses tabs (`Clientes`, `Agenda`, `Ajustes`); nesting a
second `TabRow` inside Clientes creates two horizontal indicators
within 100 px of each other and looks amateur on the Tab A9.

Instead: a **segmented pill control** in the Clientes hero, directly
under the search bar:

```
┌──────────────────────────────────────────────────────────┐
│  Buenas tardes, agent1                       [⟳ synced]  │
│                                                          │
│  113 clientes pendientes                                 │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │ 🔍 Buscar por nombre o teléfono…                   │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│   ╭─────────────╮ ╭─────────────╮ ╭─────────────╮       │
│   │ Pendientes  │ │  Recientes  │ │ Interesados │       │
│   │   ● 113     │ │      8      │ │     12      │       │
│   ╰─────────────╯ ╰─────────────╯ ╰─────────────╯       │
└──────────────────────────────────────────────────────────┘
```

- Each pill = `Surface` with rounded shape (`RoundedCornerShape(50)`),
  `tonalElevation = 1.dp` when unselected, `containerColor =
  primaryContainer` when selected.
- Inside the pill: label (`labelLarge`) + count badge (`bodyMedium`,
  on its own line on the selected pill, inline on unselected).
- A **single primary indicator dot** marks the selected pill at the
  top-left corner — same dot we use for sync status.
- Counts are reactive (`StateFlow<Int>`), update in real time.

### 4.2 List item — view-aware, not state-aware

Each view renders the SAME `LazyColumn` but with a card variant
parameterized by view kind. Three card styles:

#### 4.2.1 Pendientes card (existing — minor cleanup only)

```
┌─────────────────────────────────────────────────────┐
│  ●●  Maria López                            [📞]    │
│      +507 6680-1776                                 │
│      0 intentos · siguiente en cola                 │
└─────────────────────────────────────────────────────┘
```

#### 4.2.2 Recientes card (new — outcome-led)

```
┌─────────────────────────────────────────────────────┐
│  🔴  Maria López                          hace 23m  │
│      No interesado · "no le sirve el plan"          │
│      [📝 Agregar nota]    [📞 Llamar de nuevo]      │
└─────────────────────────────────────────────────────┘
```

- **Outcome badge** color-codes status:
  - 🟢 `INTERESTED` (success)
  - 🟡 `NO_ANSWER` (warning)
  - 🟠 `BUSY` (warning-alt)
  - 🔴 `NOT_INTERESTED` / `INVALID_NUMBER` (error)
- **Time-ago** ("hace 23 m", "hace 4 h") replaces the queue-order line
  — agents think in time on this view, not in queue position.
- **Last note preview** (one line, ellipsis at 60 chars) so the agent
  can recognize the call without re-opening.
- **Two actions** as text buttons:
  - `Agregar nota` → opens a bottom sheet, NOT PreCall (the agent is
    not calling, they are journaling). Adds a `NoteType.POST_CALL`
    note tied to the most recent interaction's `mobileSyncId`.
  - `Llamar de nuevo` → only enabled if outcome ∈ {`NO_ANSWER`,
    `BUSY`}. For `INTERESTED` the call entry point is via the
    Interesados view (where re-calling is a normal lead-handling
    action). For `NOT_INTERESTED` / `INVALID_NUMBER` we hide it —
    re-dialing those is a UX anti-pattern (and the autocall
    orchestrator skips them anyway).

#### 4.2.3 Interesados card (new — pipeline-led)

```
┌─────────────────────────────────────────────────────┐
│  🟢  Carlos Pérez                         ⏰ Mañana │
│      +507 6234-1100                       09:00 AM  │
│      Última nota: "quiere comparar planes…"         │
│      Llamado hace 3 días · 2 intentos               │
│      [📝 Nota]   [📞 Llamar]   [⏰ Re-agendar]      │
└─────────────────────────────────────────────────────┘
```

- **Follow-up chip** (top-right) shows the next scheduled follow-up,
  if any, in a friendly relative format ("Mañana 09:00", "En 2 h",
  "Atrasado 1 día"). Tappable → goes to Agenda detail.
- **Lead pipeline metadata** below the note: how long the lead has
  been warm, attempt count.
- **Three actions** because this view is the agent's lead-management
  cockpit: add note, call back, re-schedule the follow-up.

### 4.3 Empty states — meaningful, not generic

Each view has its own empty illustration + copy:

| View | Empty copy | Illustration vibe |
|---|---|---|
| Pendientes | "Cero clientes pendientes." / "Pídele al admin que te asigne más, o revisa Recientes para repasar tus llamadas del día." | check-mark / inbox-zero |
| Recientes | "No has llamado a nadie en las últimas 24 h." / "Apenas llames a alguien aparecerá aquí." | clock |
| Interesados | "Aún no tienes clientes interesados." / "Cuando marques INTERESADO en Post-Call, aparecerán aquí." | star outline |

### 4.4 Search behavior

The search bar is **scoped to the active view**. Searching while in
Recientes searches Recientes only; switching to Interesados
auto-clears the query (or keeps it, design decision pending — see
§ 9). Recommendation: **keep the query but re-scope** — agents often
search "Maria" across views to find one specific lead.

This needs three new DAO `searchByX(...)` variants mirroring
`searchByStatus`, parameterized by the view's data filter.

### 4.5 Color and elevation

Reuse the existing semantic palette from
[`ARCHITECTURE.md` § Theme](./ARCHITECTURE.md). Specifically:

- Selected pill: `primaryContainer` + `onPrimaryContainer` text.
- Unselected pill: `surfaceContainerLow` + `onSurfaceVariant` text.
- Outcome badges: pull from the `StatusColors` token set (already
  added in Phase 4.5.1 for the sync indicator + outcome chips in
  Post-Call).
- Cards: `tonalElevation = 0.dp`, `border = 1.dp` solid
  `outlineVariant`. We've moved away from shadow-heavy Material 2
  cards on this app — keep that.

### 4.6 Motion

- View switch: `AnimatedContent` with a 200 ms `slideInHorizontally` +
  `fadeIn` keyed on the active view. Direction matches pill order
  (Pendientes left, Interesados right).
- Counts on the pills: `AnimatedContent` with a vertical slide for
  the number — same micro-interaction we use for the sync indicator
  count today.
- List item changes (e.g. a client moves from Pendientes to Recientes
  after a call): rely on `animateItemPlacement()` on the
  `LazyListScope.items(...)` block. Free with Compose 1.6+.

---

## 5. Where the navigation entry points lead

Each card across the three views still leads to the same destinations
the agent already knows. We do not introduce new screens.

| Card | Tap target | Lead to |
|---|---|---|
| Pendientes — body | full card | PreCall (existing) |
| Pendientes — phone icon | the icon | PreCall (existing) |
| Recientes — body | full card | a new **Client Detail** read-only sheet (see § 6) |
| Recientes — "Agregar nota" | text button | Note bottom sheet (new, modal) |
| Recientes — "Llamar de nuevo" | text button | PreCall |
| Interesados — body | full card | PreCall (existing — PreCall already handles INTERESTED) |
| Interesados — "Nota" | text button | Note bottom sheet (same as Recientes) |
| Interesados — "Llamar" | text button | PreCall |
| Interesados — "Re-agendar" | text button | a new **Edit Follow-up** sheet (deferred — not v1.0) |
| Interesados — follow-up chip | the chip itself | Agenda → that follow-up's detail |

---

## 6. Client Detail — light read-only sheet for Recientes

Today there is no "view this client without calling them" surface.
Pre-Call is the closest thing but it is goal-oriented (the agent is
about to dial). For the Recientes view we need a calmer surface:

- Modal bottom sheet (~70 % screen height).
- Header: name + phone + outcome badge + time-ago.
- Body (scrollable):
  - All notes for this client (history), newest first.
  - All recent interactions (last 30 days), with outcome + duration.
- Footer: same two actions as the card (Add note, Call again).

This is **deferred** until after Pendientes/Recientes/Interesados ship.
The card-level "Agregar nota" button covers the immediate need; the
detail sheet is a polish item.

---

## 7. Backend coordination — one open item

### 7.1 Admin re-assignment of a previously-handled client

The user surfaced a real case: admin needs to re-assign an existing
(or formerly-rejected) client back to an agent.

**Server side (`calls-core`) — already supported:** the `/clients/admin/assign`
endpoint exists (`AssignClientsDto`). It updates `assignedTo` and
re-cancels any pending follow-ups for the previous owner. ✓

**Mobile side — what happens after the re-assignment is committed:**

| Re-assignment flavor | What happens on agent A's next sync |
|---|---|
| Admin assigns a brand-new PENDING client | `refreshAssigned(PENDING)` returns it → appears in Pendientes. ✓ Already works. |
| Admin re-assigns an INTERESTED client from agent B → A | `refreshAssigned(INTERESTED)` returns it → appears in Interesados. ✓ Already works (we just added Interesados, didn't add new sync). |
| Admin re-assigns a previously-REJECTED client back to A as PENDING | The server must explicitly **reset the status to PENDING** as part of the re-assignment, not just flip `assignedTo`. ⚠ Open question. |

**The open item:** confirm with `calls-core` whether
`/clients/admin/assign` resets status to `PENDING` for clients that
were `REJECTED`/`INVALID_NUMBER`/`CONVERTED`. If it does NOT, the
mobile app has no way to receive them — they would not appear in any
of the three views (we never query for those statuses).

**Decision needed (calls-core team):** either (a) the assign endpoint
resets status to PENDING by default with an opt-out flag, or (b) the
mobile starts querying additional statuses (`REJECTED`,
`INVALID_NUMBER`, `CONVERTED`) and exposes a fourth view "Archivados"
— more device complexity and not really useful for the agent.

**Recommendation:** option (a) — admin-side reset on re-assign. Keeps
the mobile data model simple: every re-assigned client lands in
Pendientes ready to be called fresh.

This needs to be added to
[`BACKEND_COORDINATION.md` § 5](./BACKEND_COORDINATION.md) before
implementation starts.

### 7.2 Sync impact summary

| Trigger | Existing endpoint | View impacted |
|---|---|---|
| New PENDING assigned | `GET /clients/assigned?status=PENDING` | Pendientes |
| Outcome saved (NOT_INTERESTED, NO_ANSWER, …) | `POST /sync/interactions` + `GET /clients/assigned?status=PENDING` | Pendientes (out), Recientes (in) |
| Outcome saved INTERESTED | same + `GET /clients/assigned?status=INTERESTED` | Pendientes (out), Recientes (in), Interesados (in) |
| Admin re-assigns | next periodic sync's `GET /clients/assigned?status=PENDING` | Pendientes (in) — assuming § 7.1 (a) |
| Note added late from Recientes | `POST /sync/interactions` notes payload | (no list change — server stores it) |

**Net new sync work: zero.** Every view is fed by data the app already
fetches. The redesign is purely a presentation reorganization on top
of the existing offline-first cache. That is the win.

---

## 8. Bug fix prerequisite — KI-NEW-01 (NOT_INTERESTED reverts after sync)

This redesign **assumes** the bug where a NOT_INTERESTED outcome
"comes back" after sync is fixed. Until it is, the Recientes view will
look correct but the Pendientes count will visibly bounce — a bad
first impression.

The bug is documented separately in `KNOWN_ISSUES.md` (to be added as
KI-06). Root cause hypothesis: server's `processInteractions`
catches `E11000` duplicate-key errors and **skips
`updateClientOnInteraction`**, leaving the server-side client status
stale at PENDING. Fix is server-side.

**This redesign does not block on the fix**, but the fix should land
first or in parallel so QA on the new tab sees clean data.

---

## 9. Open questions to resolve before implementation

1. **Search query persistence across view switches:** keep the query
   when the agent flips Pendientes ↔ Recientes ↔ Interesados, or
   clear it? Recommendation: **keep**.
2. **Order of pills:** Pendientes / Recientes / Interesados is the
   left-to-right narrative of the day. Alternative: Recientes leftmost
   (most recently used). Stick with the narrative order — it is more
   discoverable for new agents.
3. **Recientes window length:** 24 h is the user's stated preference.
   Consider making it a setting (12 h / 24 h / 48 h) once we have user
   feedback. v1.0: hardcoded 24 h.
4. **"Llamar de nuevo" on Recientes for INTERESTED:** currently
   suggested to hide (route via Interesados instead). User feedback
   may want it visible everywhere — easy to flip.
5. **Re-agendar from Interesados:** deferred to v1.1 (touches the
   Agenda module). For v1.0, the agent uses the Agenda tab to
   re-schedule.

---

## 10. Phasing — what ships when

| Phase | Scope | Blockers |
|---|---|---|
| **P1 — Pendientes + Recientes** | Pill selector, Recientes view, outcome-badge card, Add-note bottom sheet, Empty state for both | KI-NEW-01 fix, no backend work |
| **P2 — Interesados** | Third pill, Interesados card with follow-up chip, Empty state | None — data already syncs |
| **P3 — Client Detail sheet** | Read-only sheet from Recientes card body | None |
| **P4 — Re-assignment polish** | Admin re-assign reset to PENDING (server-side) + mobile QA pass | calls-core decision (§ 7.1) |

P1 + P2 are the v1.0 release of this redesign. P3 + P4 are v1.1.

---

## 11. Files this redesign will touch

Documenting up front so the implementation PR has no surprises.

**New:**
- `presentation/clients/components/ClientsViewSelector.kt` — segmented pill
- `presentation/clients/components/RecentClientCard.kt`
- `presentation/clients/components/InterestedClientCard.kt`
- `presentation/clients/components/AddNoteSheet.kt`
- `presentation/clients/ClientsViewKind.kt` — sealed class

**Modified:**
- `data/local/db/ClientDao.kt` — add `observeRecent(since)` + `searchRecent(...)`
- `domain/repository/ClientRepository.kt` — expose `observeRecent()`
- `data/repository/ClientRepositoryImpl.kt` — implement
- `presentation/clients/ClientsViewModel.kt` — add `viewKind`, three flows, three counts
- `presentation/clients/ClientsScreen.kt` — selector + per-view list
- `docs/BACKEND_COORDINATION.md` — append § 5.x for admin re-assign reset
- `docs/KNOWN_ISSUES.md` — KI-06 entry for the NOT_INTERESTED revert bug

**No changes:**
- `presentation/precall/*` — re-used as-is
- `data/sync/*` — no new sync paths needed (key win)
- Backend — only the optional re-assign reset (§ 7.1)

---

## 12. Approval checklist

Before implementation starts, confirm:

- [ ] Three-view model (Pendientes / Recientes / Interesados) is the
      final shape (vs adding more views like "Archivados").
- [ ] Recientes window = 24 h, hardcoded for v1.0.
- [ ] Segmented pill selector (no nested TabRow).
- [ ] Add-note bottom sheet for Recientes (not a full PreCall).
- [ ] Backend re-assign reset-to-PENDING decision (calls-core).
- [ ] KI-06 (NOT_INTERESTED revert) is documented and prioritized.
- [ ] Phasing P1 → P4 is acceptable (P1+P2 in v1.0).
