# KNOWN_ISSUES — Pending review

Defects and architectural concerns identified during code review that have
**not yet been fixed**. Each entry lists the symptom, the offending code
location, the reproduction path, and the proposed direction (without
committing to it).

This file is the parking lot for "we found it, we'll deal with it later."
Do not start work on these until they are explicitly prioritized into
[`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md).

Last updated: **2026-04-25** (KI-05 verified and closed; KI-04 reclassified)

---

## Index

| # | Severity | Title | Status |
|---|---|---|---|
| [KI-01](#ki-01--replaceall-is-destructive-and-overwrites-pending-local-changes) | 🔴 High | `replaceAll` is destructive and overwrites pending local changes | Pending review |
| [KI-02](#ki-02--race-between-two-consecutive-refreshassigned-calls-empties-clients-tab) | ✅ Closed | Race between two consecutive `refreshAssigned` calls empties Clients tab | Fixed 2026-04-26 — replaceAllByStatus is status-scoped now |
| [KI-03](#ki-03--no-in-call-guard-for-data-refresh) | ✅ Closed | No "in-call" guard for data refresh | Fixed — `InCallGate` gates the pull half of every sync |
| [KI-04](#ki-04--unassigned-client-leaves-orphan-local-records-with-no-reconciliation) | 🟠 Medium-High | Unassigned client leaves orphan local records with no reconciliation | Pending review (downgraded from 🔴 after KI-05) |
| [KI-05](#ki-05--unverified-cascade-behavior-on-client-fk) | ✅ Closed | CASCADE behavior on Client FK — verified: no FK declared | Closed 2026-04-25 |

---

## KI-01 — `replaceAll` is destructive and overwrites pending local changes

**Severity:** 🔴 High — silent data loss.

**Where:** [`data/local/db/ClientDao.kt:37-43`](../app/src/main/java/com/project/vortex/callsagent/data/local/db/ClientDao.kt#L37)

```kotlin
@Transaction
suspend fun replaceAll(clients: List<ClientEntity>) {
    deleteAll()         // wipes the whole `clients` table
    upsert(clients)     // re-inserts only what the server returned
}
```

**Symptom:** After the agent applies a local-only change (e.g. via
`ClientRepositoryImpl.applyInteractionLocally(...)` flipping a client to
`INTERESTED`, or `updateLastNoteLocally(...)`), any subsequent call to
`refreshAssigned(...)` blows that change away because `deleteAll() +
upsert(serverList)` replaces the whole row with the server's older copy.

**Reproduction:**

1. Agent on Pre-Call → calls a client → marks INTERESTED.
2. `applyInteractionLocally` updates the local row to `status = INTERESTED`.
3. Before `SyncManager.syncAll()` finishes pushing the new interaction
   to the server, `ClientsViewModel` is reopened (or WorkManager fires
   the periodic sync).
4. `refreshAssigned(PENDING)` runs → `deleteAll()` wipes the row →
   `upsert(serverList)` reinserts it with the **old** server status.
5. The agent's local optimistic update is lost.

**Proposed direction (not committed):** switch `replaceAll` to a
**diff-based reconciliation** (insert new, update changed, delete only
ids no longer returned), and have the merge logic preserve local fields
that have not yet been pushed (track via a `localDirty` flag or via the
existing `syncStatus` column on the related entities).

**Blocked by:** decision on optimistic-update strategy. Coordinate with
`calls-core` on whether the server response includes a `lastModified`
timestamp we can compare against.

---

## KI-02 — Race between two consecutive `refreshAssigned` calls empties Clients tab ✅ FIXED

**Resolution (2026-04-26):** `ClientDao` got a new `replaceAllByStatus`
that scopes the delete to a single status. `ClientRepositoryImpl.refreshAssigned`
now calls it instead of the destructive `replaceAll`. Calling
`refreshAssigned(PENDING)` then `refreshAssigned(INTERESTED)` no longer
clobbers the PENDING set. Confirmed against the production sync log
that triggered the report: agent had 113 PENDING + 0 INTERESTED →
without the fix the table ended at 0; with the fix it stays at 113.

The original report follows for historical context.

---

**Severity (original):** 🔴 High — manifest UX bug after the first post-call sync.

**Where:** [`data/sync/SyncManager.kt:131-138`](../app/src/main/java/com/project/vortex/callsagent/data/sync/SyncManager.kt#L131)

```kotlin
private suspend fun refreshServerState() {
    runCatching { clientRepo.refreshAssigned(ClientStatus.PENDING) }
    runCatching { clientRepo.refreshAssigned(ClientStatus.INTERESTED) }
    runCatching { followUpRepo.refreshAgenda() }
}
```

**Symptom:** Each `refreshAssigned(...)` call performs `deleteAll()` over
the whole `clients` table (see KI-01). Calling them sequentially means:

1. After step 1, table holds only PENDING clients.
2. Step 2 deletes everything **including the PENDING set just inserted**,
   then inserts only INTERESTED.
3. Final state: table holds only INTERESTED clients.

The Clients tab observes `status = PENDING` → emits an empty list → the
agent sees "No clients assigned yet" right after every sync.

**Reproduction:**

1. Login, navigate to Clients → list populates.
2. Trigger a sync (post-call, force-sync, periodic, or reconnect).
3. Watch the Clients tab go empty for a few hundred ms / permanently
   until something triggers `refreshAssigned(PENDING)` again.

**Proposed direction (not committed):** either (a) make a single
`refreshAll(...)` API method that returns the full assigned set in one
call and uses a single diff-based merge, or (b) scope `replaceAll` to
the status it just fetched (only delete rows of that status before
inserting). Option (a) is cleaner and reduces RTTs.

**Blocked by:** API shape decision — coordinate with `calls-core` on
whether `GET /clients/assigned` can return both PENDING + INTERESTED in
one envelope.

---

## KI-03 — No "in-call" guard for data refresh ✅ CLOSED

**Resolution:** New `InCallGate` (`data/sync/InCallGate.kt`) reads
the live `CallManager.callState` and reports whether the agent is
mid-call (Dialing, Ringing, or Active).

**Where the gate is enforced:**
- `SyncManager.refreshServerState()` checks the gate on entry. If
  the agent is in a call, the **pull half** (refreshAssigned PENDING
  + INTERESTED + refreshAgenda) is **skipped entirely**.

**What still runs during a call:**
- The **push half** (`POST /sync/interactions`). Sending the agent's
  locally-committed data to the server doesn't touch the UI; it
  just keeps the server up-to-date.
- `markSynced` writes against the local DB but only touch
  `syncStatus` columns the agent doesn't see.

**Recovery after the call ends:**
- `PostCallViewModel.save()` triggers an immediate sync via
  `syncScheduler.triggerImmediateSync()`, which re-runs
  `refreshServerState()` with the gate now open.
- Worst case (agent ends the call without going through Post-Call,
  e.g. network drop during dial): the deferred refresh waits up to
  20 min for the next periodic tick. Acceptable — no data loss,
  just brief staleness.

**Why the original spec changed:** the original "block during
Pre-Call too" was overkill. Pre-Call has no live audio so a
silent refresh during it is harmless. Gating only on
`Dialing/Ringing/Active` avoids unnecessary deferrals while still
protecting the moments where the agent is actively talking and
shouldn't see the row shift under their fingers.

**Files:**
- New: `data/sync/InCallGate.kt`
- Modified: `data/sync/SyncManager.kt`
  (`refreshServerState` short-circuits when gate is closed).

---

## KI-04 — Unassigned client leaves orphan local records with no reconciliation

**Severity:** 🟠 Medium-High — no silent local data loss (verified by
KI-05), but permanent unsyncable records and a UI dead-end.

**Where:** Architectural. No single file owns the gap.

**Symptom:** When the admin reassigns or unassigns a client in the web
panel:

1. The next `GET /clients/assigned` no longer includes that client.
2. `replaceAll(...)` deletes the local `ClientEntity` row.
3. Any `InteractionEntity`, `NoteEntity`, or `FollowUpEntity` the agent
   created locally for that client **survives** in their own tables
   (no FK CASCADE — see KI-05 closure). They become **logical orphans**:
   - `clientId` points to a row that no longer exists locally.
   - Any UI that joins or looks up the client will render empty / break.
   - When those records try to sync, the server may reject them (403/404)
     because the agent no longer has the assignment → records stuck in
     `PENDING` forever, never visible to the user, never retryable.

There is no UI surface for "we couldn't sync these N records — what now?"

**Reproduction:**

1. Admin assigns client X to agent A.
2. Agent A places a call to X, marks INTERESTED, schedules follow-up.
3. **Before** the device syncs, admin reassigns X to agent B.
4. Agent A's device runs `syncAll()` → push step rejected (or accepted
   silently with weird state, depending on backend behavior).
5. Local records remain `PENDING` forever, no error surfaced.

**Proposed direction (not committed):**

- On refresh, **diff** the incoming list vs local; if a client is gone
  locally but has unsynced child records, **keep the client row** with
  a new flag `localStatus = ORPHANED` and surface a "Reconcile" UI in
  Settings so the agent (or a support flow) can either force-push or
  discard.
- Alternatively (simpler), have the backend accept pushes for
  previously-assigned clients and route them to the new owner — moves
  the complexity off the device. Requires `calls-core` decision.

**Blocked by:** product + backend decision. Both options need
`calls-core` alignment.

---

## KI-05 — CASCADE behavior on Client FK ✅ CLOSED

**Resolution:** Verified on 2026-04-25 — **no `@ForeignKey` declared
in any of the four entities**. `InteractionEntity`, `NoteEntity`, and
`FollowUpEntity` reference `clientId` as a plain `String` column, with
no Room-level FK enforcement.

**Implication for adjacent issues:**

- **KI-01 stays 🔴 High** but for the original reason only: it
  overwrites optimistic local changes on the `ClientEntity` row. It
  does NOT destroy `Interaction`/`Note`/`FollowUp` rows (those live in
  independent tables).
- **KI-04 downgraded to 🟠 Medium-High** — no silent local data loss,
  but logical orphans + permanent unsyncable records remain a real
  problem.
- The "third option" (no FK) was the actual reality. Orphans accumulate.

**Files inspected:**

- [`ClientEntity.kt`](../app/src/main/java/com/project/vortex/callsagent/data/local/entity/ClientEntity.kt)
- [`InteractionEntity.kt`](../app/src/main/java/com/project/vortex/callsagent/data/local/entity/InteractionEntity.kt)
- [`NoteEntity.kt`](../app/src/main/java/com/project/vortex/callsagent/data/local/entity/NoteEntity.kt)
- [`FollowUpEntity.kt`](../app/src/main/java/com/project/vortex/callsagent/data/local/entity/FollowUpEntity.kt)

No code change. Status changed from "Pending review" to "Closed".

---

## How this file is maintained

- New issues found during code review or testing → add a `KI-NN` entry
  here with full repro + suggested direction.
- When prioritized into a phase → move the work item into
  `DEVELOPMENT_PLAN.md` and update the entry's **Status** to
  `Scheduled in Phase X`.
- When fixed → update **Status** to `Fixed in commit <sha>` and leave
  the entry as a historical record. Do not delete entries — they
  document past failure modes for future review.
