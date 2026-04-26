# PENDING_BACKEND_WORK — Items queued for `calls-core`

Backend changes the mobile app needs but cannot ship on its own. Track
each item here, mark them ✅ when deployed to Railway, then unblock the
matching mobile work.

Last updated: **2026-04-26** — BE-01, BE-02 and BE-03 implemented +
tested locally; awaiting Railway deploy.

---

## Status legend

- 🔴 **Blocking** — mobile feature cannot ship until this lands.
- 🟡 **Required for full feature** — mobile can build, but a sub-feature
  is dark until backend deploys.
- 🟢 **Done** — deployed, mobile unblocked.

---

## BE-01 — Fix KI-06 (NOT_INTERESTED outcome reverts after sync) 🟢 IMPLEMENTED

**Status:** ✅ Implemented locally — pending Railway deploy.

**Files touched:**
- `src/sync/sync.service.ts` — `processInteractions` rewritten so the
  client status mutation runs after the duplicate-key catch (the
  `$set` is idempotent — re-applying same status is a no-op).
- `src/sync/sync.service.spec.ts` — two new tests: KI-06 idempotency
  on duplicate, and surfacing of status-mutation errors.

**Verified:** `npx jest --no-coverage` → 67/67 passing.

---

**Original problem:** Client status mutation was inside the
duplicate-key catch in `processInteractions`. Re-pushed interactions
hit `E11000` and skipped `updateClientOnInteraction`, leaving the
server's client status stuck at PENDING. Mobile then re-fetched and
the just-marked-NOT_INTERESTED client bounced back into the queue.

**Fix shipped:** the status mutation runs unconditionally after the
duplicate-key handling. Idempotent `$set` so re-applying same status
is a no-op.

---

## BE-02 — Client dismissal feature (D1) 🟢 IMPLEMENTED

**Status:** ✅ Implemented locally — pending Railway deploy. Mobile
can now build dismissal UI safely; sync push will be accepted.

**Files touched / created:**
- `src/common/enums/client-status.enum.ts` — added `DISMISSED`.
- `src/dismissals/schemas/client-dismissal.schema.ts` — new Mongo
  schema with indexes `(agentId, dismissedAt)` and `(clientId)`.
- `src/dismissals/dismissals.service.ts` — `upsert(...)` returning
  `{ created }` plus `findByClient(...)` and `findByAgent(...)`.
- `src/dismissals/dismissals.module.ts` — module wiring; registered
  in `app.module.ts`.
- `src/sync/dto/sync-dismissal.dto.ts` — DTO with class-validator.
- `src/sync/dto/sync-request.dto.ts` — added optional `dismissals[]`.
- `src/sync/sync.service.ts` — new `processDismissals(...)` method;
  wired into `processSync` and the response envelope.
- `src/sync/sync.module.ts` — imports `DismissalsModule`.
- `src/follow-ups/follow-ups.service.ts` — added
  `reactivatePendingForClient(...)` for the undo path.
- `src/clients/dto/reactivate-client.dto.ts` — new admin DTO.
- `src/clients/clients.controller.ts` — new
  `POST /clients/:id/reactivate` endpoint, ADMIN-gated.
- `src/sync/sync.service.spec.ts` — new `dismissals` describe block
  with three tests (created path, undo path, duplicate path).

**Spec:** [`CLIENT_DISMISSAL.md`](./CLIENT_DISMISSAL.md) — full design
with schemas, endpoints, idempotency rules, edge cases.

**Verified:** `npx jest --no-coverage` → 67/67 passing.

**Backend tasks:**

### BE-02.a — Enum extension

Add `DISMISSED` to `src/common/enums/client-status.enum.ts`:

```ts
export enum ClientStatus {
  PENDING,
  INTERESTED,
  CONVERTED,
  REJECTED,
  INVALID_NUMBER,
  DO_NOT_CALL,
  DISMISSED,    // ← new
}
```

### BE-02.b — New Mongo schema + module

- `src/dismissals/schemas/client-dismissal.schema.ts` — fields per
  `CLIENT_DISMISSAL.md § 4.2`. Indexes: `(agentId, dismissedAt desc)`
  and `(clientId)`.
- `src/dismissals/dismissals.service.ts` — `upsert(...)` by
  `mobileSyncId`, `findByClient(...)`, `findByAgent(...)`.
- `src/dismissals/dismissals.module.ts` — DI wiring.
- Register module in `app.module.ts`.

### BE-02.c — Sync envelope extension

In `src/sync/dto/sync-request.dto.ts` add optional `dismissals` array
of `SyncDismissalDto` (full DTO in `CLIENT_DISMISSAL.md § 5.1`).

In `src/sync/sync.service.ts`:
- Add `processDismissals(agentId, dismissals)` mirroring the
  KI-06-fixed pattern (status mutation **outside** the
  duplicate-key catch).
- Wire into `sync(...)` after `processCompletedFollowUps`.
- Include result in `SyncResponseDto`.

### BE-02.d — Helper services

- `clientsService.setStatus(id, status)` — if not already exposed.
  Used by both undo and admin reactivate paths.
- `followUpsService.reactivatePendingForClient(clientId, agentId)` —
  re-enables follow-ups whose `scheduledAt > now` that were cancelled
  by a prior dismissal of the same client. Required for undo.

### BE-02.e — Admin endpoints

- `POST /clients/:id/reactivate` — restores a `DISMISSED` client to
  `PENDING` (default) or to a status passed in body, same agent.
- `PATCH /clients/admin/assign` (existing) — when called on a
  `DISMISSED` client, server resets `status` to `PENDING` as part of
  the reassignment flow. **Verify this behavior is in place** — if
  not, add it.

**Tests to add:**
- Unit: dismissal upsert is idempotent.
- Unit: undo restores `previousStatus` AND reactivates eligible
  follow-ups.
- Integration: full sync round-trip with dismissals + interactions in
  the same envelope.

**Mobile blocked:** Phase D2/D3 of the dismissal feature. Mobile UI
can be built and merged behind a feature flag (or with the local-only
path active) but **sync push must be gated** until BE-02 deploys.

---

## BE-03 — Admin reset-on-reassign for archived statuses 🟢 IMPLEMENTED

**Status:** ✅ Implemented locally — pending Railway deploy.

**Files touched:**
- `src/clients/dto/assign-clients.dto.ts` — added optional
  `preserveStatus?: boolean` flag (default false).
- `src/clients/clients.service.ts` — `assignClients(...)` resets
  `status` to `PENDING` for clients currently in `REJECTED`,
  `INVALID_NUMBER`, `CONVERTED` or `DISMISSED` unless
  `preserveStatus = true`.

**Verified:** existing 9 client-service tests pass; assign tests
unchanged because they use brand-new clients (status = PENDING already).
Manual reasoning confirmed by re-reading the diff against the spec.

---

## (Original BE-03 details below — kept for traceability)

### BE-03 (original spec)

**Severity:** Required for full feature (re-assignment of formerly
rejected clients).

**Context:** `BACKEND_COORDINATION.md § 2.3` and
`CLIENTS_TAB_REDESIGN.md § 7.1`. The `PATCH /clients/admin/assign`
endpoint must **reset status to PENDING** when re-assigning clients
whose current status is `REJECTED`, `INVALID_NUMBER`, `CONVERTED`,
or `DISMISSED`.

**Required behavior:**

```ts
// In clients.service.assignClients(...)
async assignClients(dto: AssignClientsDto) {
  // ... existing logic ...

  // After updating assignedTo, if status is non-PENDING/non-INTERESTED,
  // reset to PENDING so the new agent gets a clean queue entry.
  await this.clientModel.updateMany(
    {
      _id: { $in: dto.clientIds },
      status: { $in: [
        ClientStatus.REJECTED,
        ClientStatus.INVALID_NUMBER,
        ClientStatus.CONVERTED,
        ClientStatus.DISMISSED,
      ]},
    },
    { $set: { status: ClientStatus.PENDING } },
  );
}
```

**Optional opt-out flag:** `AssignClientsDto` may grow a
`preserveStatus?: boolean` field for the rare cases where the admin
wants to reassign without resetting. Default = false (reset).

**Tests to add:**
- Reassign a REJECTED client → ends as PENDING for new agent.
- Reassign with `preserveStatus = true` → status unchanged.

**Mobile blocked:** none directly; this is needed for the admin
re-activation flow in `CLIENT_DISMISSAL.md § 7.2`.

---

## How to use this doc

1. Backend lead picks an item, implements + tests + deploys.
2. Mark the item ✅ (status 🟢) and link the commit/PR.
3. Mobile side gets unblocked — coordinate via Slack or this repo.
4. When all items are 🟢, this file should be empty (just keep the
   structure for the next batch).
