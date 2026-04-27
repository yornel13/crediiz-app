# PHASE_2_BACKLOG — Items deferred from v1.0

Tracker for product / engineering items we explicitly decided to ship
**after** the 4-agent v1.0 rollout. Each entry says what, why deferred,
and what would trigger picking it up.

Last updated: **2026-04-26**

---

## P2-01 — Orphan-INTERESTED ("Sin agendar") soft limit + warnings

**Where:** Agenda → "Sin agendar" section.

**The problem we don't have data on yet:** an agent who consistently
fails to schedule follow-ups (or an admin who reassigns INTERESTED
clients without resetting their pipeline) can accumulate many orphan
leads. With no limit, the section keeps growing and the agent loses
visibility on which leads are actually warm.

**v1.0 decision:** **no limit, no warning**. Ship and observe. We
don't have ground truth on what "too many" looks like for a real
agent's day.

**v2 trigger:** if any agent's "Sin agendar" count exceeds **20**
sustained for more than 7 days, OR if a supervisor reports it as a
problem during pilot.

**Implementation when triggered:**

1. **Soft warning at the section header** when `count > 20`:
   ```
   SIN AGENDAR (24)  ⚠ Demasiados leads sin agendar
   ```
   Plus subtle copy: "Estos leads están en riesgo de enfriarse. Agenda
   un seguimiento o descarta los que ya no aplican."

2. **Empty-state coaching** when the count first crosses the
   threshold: a one-time bottom sheet explaining the section and
   suggesting actions (schedule, dismiss).

3. **Admin-side report**: in the web panel, "Leads at risk per agent"
   table showing the count of orphan-INTERESTED per agent over time.
   Helps the supervisor coach the team.

4. **Optional auto-archive** (decision deferred): after 30 days as
   orphan-INTERESTED with no calls, automatically convert to
   `DISMISSED` with reasonCode = `STALE_AUTO`. Default off; admin
   opt-in per agent or per campaign.

**Acceptance criteria:**
- Agent sees the warning when it applies; doesn't see it otherwise.
- Coaching sheet shows once per agent, dismissable, doesn't re-appear.
- Admin can sort agents by this metric in the web panel.
- Auto-archive (if enabled) fires only on `lastCalledAt < now - 30d`
  AND `status == INTERESTED` AND no recent dismissal events.

**Effort estimate:** ~1 day mobile + ~1 day backend (auto-archive
job) + ~1 day admin web panel report. Total ~3 days.

---

## P2-02 — Client Detail read-only sheet

**Where:** opened from Recientes / Sin agendar / Pre-Call.

**v1.0 decision:** deferred. The Pre-Call screen already shows
`lastNote`, the agenda card shows last note preview, and the call
flow re-uses Pre-Call as the detail surface. It's not the prettiest
detail view but it works.

**v2 trigger:** when agents start asking "let me see all the notes
on this client at once". Real signal of need.

**Implementation:** modal bottom sheet (~70 % screen height) with:
- Header: avatar + name + phone + status pill.
- All notes for the client, grouped by date, newest first.
- Last 30 days of interactions with outcome + duration.
- Footer actions: Add note, Call, (if INTERESTED) Re-agendar.

---

## P2-03 — Re-schedule follow-up from Agenda card

**Where:** Agenda → Próximas / Sin agendar card overflow menu.

**v1.0 decision:** deferred. To re-agenda a follow-up today, the
agent has to call the client and mark INTERESTED again to trigger
the date picker. Not elegant but functional.

**v2 trigger:** during pilot if agents flag the friction.

**Implementation:** add `Re-agendar` to the overflow menu next to
`Descartar` on Agenda cards. Opens the same date/time picker as the
Post-Call follow-up form. Cancels the existing pending follow-up
(server-side `cancelPendingForClient`) and creates a fresh one
with the new datetime.

---

## P2-05 — Where to surface the "Sold" outcome

**Status:** 🟡 Open question — pulled from v1.0 mid-implementation.

**What we tried:** added `CallOutcome.SOLD` as a 6th button in
Post-Call alongside INTERESTED / NOT_INTERESTED / NO_ANSWER /
BUSY / INVALID_NUMBER. It mapped to `ClientStatus.CONVERTED` and
got a "Sold" badge in Recientes.

**Why we rolled it back from mobile (kept on backend):**
1. The placement felt wrong — Post-Call is the wrap-up of a call,
   but most credit sales close after multiple touchpoints, not on
   the same call where the agent first marked INTERESTED.
2. The naming "Sold" was disputed — the lead funnel has stages
   between "interested" and "money received", and a single button
   doesn't capture the difference.

**What's still in place:**
- Backend: `CallOutcome.SOLD` enum value + `OUTCOME_TO_STATUS`
  mapping → `CONVERTED`. **Deployed on Railway.** Not removed
  because it's harmless and re-enabling it just means re-adding
  the enum on mobile.
- Mobile: enum value removed, all branches removed, no UI surfaces
  it. The agent has 5 outcome buttons again.

**Questions to resolve before re-introducing:**

1. **Where does the agent register a sale?**
   - Inside Post-Call as a 6th outcome (what we tried).
   - As a separate action on the INTERESTED lead in Agenda
     (e.g. "Mark as Sold" overflow on an INTERESTED card after the
     final follow-up).
   - As a new section/screen "Cerrar venta" with its own form
     (collect amount, terms, etc.).
2. **What's the name?** Sold / Cerrado / Convertido / Aprobado.
   Different verticals call this differently.
3. **What metadata does the agent need to capture at sale-close
   time?** Just the outcome, or also amount / product / contract
   reference?
4. **Who else uses this signal?**
   - Admin web panel for reports.
   - Future CRM integration.
   - Commission calculation per agent.

**Recommended path when re-prioritized:**

1. Spec the sale-close metadata with the product owner (P2 design
   doc).
2. Decide placement — agree on the canonical "where the agent says
   yes-this-converted".
3. Re-enable `CallOutcome.SOLD` (or rename to a better name) on
   mobile with the agreed placement.
4. Adjust `AutoCallOrchestrator.shouldAutoAdvanceFor` to stop the
   countdown for the new outcome.
5. Add the corresponding StatCard to the auto-call session
   summary.
6. Update `MANUAL_USUARIO.md` with the chosen flow.

**Effort estimate:** ~1-2 days mobile + product spec time. Backend
change is zero or trivial.

---

## P2-04 — "My activity" stats on mobile (UX-1)

**Where:** new tab or new card in Settings → Account.

**v1.0 decision:** deferred. The agent doesn't need stats to do
their job. Stats live in the admin web panel.

**v2 trigger:** when the agents themselves ask for a personal
dashboard. Motivational gamification helps retention if pitched
right.

**Implementation:** see `docs/AGENT_UX_BACKLOG.md` UX-1 entry.

---

## How to use this file

Items here are **frozen until prioritized**. When a trigger fires:

1. Move the item to `DEVELOPMENT_PLAN.md` as a scheduled task.
2. Update the entry's status here to `Scheduled in vNext`.
3. When done, move to a "Shipped" section in this file or delete.

Do not let items pile up here without re-evaluation — every quarter
we should sweep this file and either schedule or kill items that no
longer fit the product direction.
