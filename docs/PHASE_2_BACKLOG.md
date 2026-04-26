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
