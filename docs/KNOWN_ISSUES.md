# KNOWN_ISSUES — Pending review

Defects and architectural concerns identified during code review that have
**not yet been fixed**. Each entry lists the symptom, the offending code
location, the reproduction path, and the proposed direction (without
committing to it).

This file is the parking lot for "we found it, we'll deal with it later."
Do not start work on these until they are explicitly prioritized into
[`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md).

Last updated: **2026-04-24** (initial — sync/refresh review)

---

## Index

| # | Severity | Title | Status |
|---|---|---|---|
| [KI-01](#ki-01--replaceall-is-destructive-and-overwrites-pending-local-changes) | 🔴 High | `replaceAll` is destructive and overwrites pending local changes | Pending review |
| [KI-02](#ki-02--race-between-two-consecutive-refreshassigned-calls-empties-clients-tab) | 🔴 High | Race between two consecutive `refreshAssigned` calls empties Clients tab | Pending review |
| [KI-03](#ki-03--no-in-call-guard-for-data-refresh) | 🟡 Medium | No "in-call" guard for data refresh | Pending review |
| [KI-04](#ki-04--unassigned-client-leaves-orphan-local-records-with-no-reconciliation) | 🔴 High | Unassigned client leaves orphan local records with no reconciliation | Pending review |
| [KI-05](#ki-05--unverified-cascade-behavior-on-client-fk) | ⚠️ To verify | Unverified CASCADE behavior on Client FK could destroy PENDING interactions | Pending review |

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

## KI-02 — Race between two consecutive `refreshAssigned` calls empties Clients tab

**Severity:** 🔴 High — manifest UX bug after the first post-call sync.

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

## KI-03 — No "in-call" guard for data refresh

**Severity:** 🟡 Medium — UX flicker; potentially confusing during a live call.

**Where:** Not in any single file — it's a missing concept across
`SyncManager`, `ConnectivityObserver`, and the (yet-to-build) Telecom
layer.

**Symptom:** A WorkManager periodic tick or a connectivity-restored
event can fire `syncAll()` while the agent is mid-call (Pre-Call,
In-Call, or Post-Call). The Room flow then emits an updated client
list under the agent — counters change, status badges flip, the row
they were looking at may move or disappear.

**Reproduction:**

1. Agent navigates Clients → Pre-Call for client X.
2. Periodic sync fires (~20 min mark) or connectivity is restored.
3. `SyncManager.syncAll()` runs in `Dispatchers.IO`, no awareness of
   the foreground UI state.
4. Pre-Call's observed flow re-emits — if X was reassigned or its
   server-side status changed, the visible card updates underneath
   the agent's hands.

**Proposed direction (not committed):** introduce a lightweight
`InCallGate` (singleton `StateFlow<Boolean>`) flipped to `true` from
the entry of Pre-Call until the exit of Post-Call. `SyncManager.syncAll()`
checks the gate on entry and either (a) waits for the gate to close,
or (b) only does the **push** half (no `refreshServerState()`) while the
gate is open. The agent's outcome and notes are still pushed; the
re-fetch is deferred until they're done.

**Blocked by:** Phase 3 (Telecom). The gate hooks naturally into
`CallManager` once that exists.

---

## KI-04 — Unassigned client leaves orphan local records with no reconciliation

**Severity:** 🔴 High — silent data loss + permanent unsyncable records.

**Where:** Architectural. No single file owns the gap.

**Symptom:** When the admin reassigns or unassigns a client in the web
panel:

1. The next `GET /clients/assigned` no longer includes that client.
2. `replaceAll(...)` deletes the local row.
3. Any `InteractionEntity`, `NoteEntity`, or `FollowUpEntity` the agent
   created locally for that client and that has not yet synced is
   either:
   - Deleted along with the client (if FK CASCADE — see KI-05), causing
     **silent loss of agent work**, OR
   - Orphaned, with the server later rejecting the sync push (the agent
     no longer has the assignment) → records stuck in `PENDING`
     forever, never visible to the user, never retryable.

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

## KI-05 — Unverified CASCADE behavior on Client FK

**Severity:** ⚠️ To verify — could be 🔴 High or 🟢 None depending on
configuration.

**Where:** [`data/local/entity/ClientEntity.kt`](../app/src/main/java/com/project/vortex/callsagent/data/local/entity/ClientEntity.kt)
and the FK declarations on `InteractionEntity`, `NoteEntity`,
`FollowUpEntity`.

**Concern:** If any of those entities declare a foreign key to
`clients(id)` with `onDelete = ForeignKey.CASCADE`, then the destructive
`deleteAll()` in `replaceAll` (KI-01) silently destroys all child
records as well — including PENDING interactions and notes that have
not yet synced. That would be **silent loss of agent work**.

If the FK is `RESTRICT`, the `deleteAll()` would throw on rows with
children — would break refresh entirely. Neither option is correct
given the current `replaceAll` strategy.

**Action:** read the four entity files and document the actual FK
configuration. Decision flows from there:

- If CASCADE → fix is mandatory before any production rollout (combine
  with KI-01 fix).
- If RESTRICT → confirms KI-01 is broken in a different way (refresh
  silently fails when there are unsynced children).
- If no FK → orphans accumulate (KI-04).

**Blocked by:** nothing — this is a 5-minute verification task. Just
not done yet.

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
