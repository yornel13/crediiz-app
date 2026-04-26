# BACKEND_COORDINATION — `calls-agends` ↔ `calls-core`

Single source of truth for the contract between the Android app
(`calls-agends`) and the NestJS backend (`calls-core`). When something
changes on either side that the other consumes, document it here.

Last updated: **2026-04-26** — added § 2.2 (client dismissal feature)
and § 2.3 (admin reactivate / reassign-after-dismissal endpoints).
Previous update: 2026-04-25 (`direction` field synced; mobile v1.0
contract locked).

---

## 1. Endpoints the mobile app consumes

| Method | Path | Purpose | Mobile call site |
|---|---|---|---|
| `POST` | `/auth/login` | Agent login → JWT | `AuthApi.login` |
| `GET`  | `/clients/assigned?status=PENDING` | Agent's queue | `ClientsApi.getAssigned`, called from `LoginViewModel.hydrate()` and the periodic sync |
| `GET`  | `/follow-ups/agenda` | Agent's follow-ups | `FollowUpsApi.getAgenda` |
| `POST` | `/sync/interactions` | Push pending interactions / notes / follow-ups / completed-followups / **dismissals** (see § 2.2) | `SyncApi.sync` (orchestrated by `SyncManager`) |

All routes require the `AGENT` role (JWT bearer in `Authorization`
header — handled by `AuthInterceptor` on the mobile side).

---

## 2. Locked v1.0 schema items

### 2.1 `InteractionEntity.direction` (Phase 3.5 Option B)

**Status:** ✅ end-to-end (2026-04-25).

| Layer | Form |
|---|---|
| Mongo schema (`calls-core`) | `direction: { type: String, enum: CallDirection, default: 'OUTBOUND' }` |
| `SyncInteractionDto` (NestJS) | `@IsOptional @IsEnum(CallDirection) direction?: CallDirection` |
| `sync.service.processInteractions` | `direction: item.direction ?? CallDirection.OUTBOUND` |
| `SyncInteractionDto` (Kotlin/Moshi) | `val direction: String` (always sent) |
| `Interaction.toSyncDto()` (mobile) | `direction = direction.name` |

**Backwards compatibility:** the field is optional on the DTO and
defaulted on the schema. Older mobile builds (pre Phase 3.5) keep
working — every interaction they push is treated as `OUTBOUND`.

**Index for queries:** `(agentId, direction, callStartedAt desc)` —
ready for a future admin dashboard breakdown by direction.

### 2.2 Agent-initiated client dismissal (Phase D1)

**Status:** 📐 Design approved, pending implementation. See full
spec in [`CLIENT_DISMISSAL.md`](./CLIENT_DISMISSAL.md).

**Mobile sends a new array in the existing sync envelope:**

```ts
// Added to SyncRequestDto
@IsOptional @IsArray @ValidateNested({ each: true })
@Type(() => SyncDismissalDto)
dismissals?: SyncDismissalDto[];

class SyncDismissalDto {
  @IsString mobileSyncId!: string;          // UUID, idempotency key
  @IsString clientId!: string;
  @IsEnum(ClientStatus) previousStatus!: ClientStatus;
  @IsOptional @IsString @MaxLength(200) reason?: string;
  @IsOptional @IsString reasonCode?: string;
  @IsDateString dismissedAt!: string;
  @IsBoolean undone!: boolean;
  @IsOptional @IsDateString undoneAt?: string;
  @IsDateString deviceCreatedAt!: string;
}
```

**New `ClientStatus` value:** `DISMISSED` (added to enum, between
`DO_NOT_CALL` and any future values).

**New Mongo collection:** `clientDismissals`. Schema in
[`CLIENT_DISMISSAL.md` § 4.2](./CLIENT_DISMISSAL.md#42-new-clientdismissal-entity).
Indexes: `(agentId, dismissedAt desc)` and `(clientId)`.

**Server processing rules** (critical — locked contract):

1. `dismissalsService.upsert({ mobileSyncId, ... })` — idempotent by
   `mobileSyncId` unique index.
2. `clientsService.setStatus(...)` is called **outside** the
   duplicate-key catch (lesson from KI-06). If `undone === true`,
   restore `previousStatus`; otherwise set to `DISMISSED`.
3. If dismissed: `followUpsService.cancelPendingForClient(clientId,
   agentId, 'Client dismissed by agent')`.
4. If undone: `followUpsService.reactivatePendingForClient(clientId,
   agentId)` — only reactivates follow-ups whose `scheduledAt > now`.

**Idempotency tested cases:**

- Same dismissal pushed twice → second hit returns `'duplicate'`,
  status mutation is a no-op (already DISMISSED).
- Dismissal then undone offline, both pushed in same batch → mobile
  collapses to a single dismissal with `undone = true`.
- Dismissal arrives after admin reactivation already happened →
  dismissal recorded in audit, status NOT mutated (last-write-wins
  by `updatedAt` on the client document).

### 2.3 Admin reactivate / reassign endpoints (Phase D4)

**For the admin web panel only** — the mobile app does not consume
these endpoints. Documented here so both sides stay aligned.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/clients/:id/reactivate` | Restore a `DISMISSED` client to `PENDING` (default) or `INTERESTED`, keeping the same `assignedTo`. Records audit event. |
| `PATCH` | `/clients/admin/assign` (existing) | Already supports reassigning. When called on a `DISMISSED` client, server resets status to `PENDING` automatically as part of the reassignment. |

The admin's authority is **unconditional** — these endpoints work
regardless of the 24-hour mobile undo window. After admin acts,
mobile picks up the change on the next `refreshAssigned(...)`.

**Admin queries the dismissal audit** via the existing client detail
endpoint, which is extended to embed the dismissal history array on
the client document response (or via a dedicated
`GET /clients/:id/dismissals` if we prefer to keep the main payload
small — implementation choice on the backend side).

---

## 3. Items NOT in scope for v1.0 backend

The mobile app does **not** require any of the following for v1.0.
They are listed for future coordination only:

- `MissedCallEntity` — device-only ledger of incoming calls the agent
  didn't answer. Never synced to backend by design (see
  `TELECOM_ARCHITECTURE.md` § 6.2).
- `GET /reports/agent/me?from=&to=` — for the personal-activity view
  on mobile (UX-1 in v1.1). Not part of v1.0 — deferred. **GATE-C.**
- Cross-agent dashboards / reports — admin panel concern, already
  spec'd in `calls-core/docs/MVP_OVERVIEW.md`.

---

## 4. Run / deploy / use checklist

### 4.1 Local backend (development)

```bash
cd /Users/yornel/projects/vortex/calls-core
npm install                 # if first run
npm run start:dev           # watches src/ and reloads
```

Default port (per Nest): 3000. Verify with
`curl http://localhost:3000/health` (the health endpoint exists at
`src/health/`).

### 4.2 Re-seed the dev DB

```bash
cd /Users/yornel/projects/vortex/calls-core
npm run seed
```

Creates:

- `admin@test.com / test1234`
- `agent1@test.com / test1234` (≈33% of the seeded clients)
- `agent2@test.com / test1234` (≈33% of the seeded clients)
- A pool of unassigned clients (≈34%)

Use `agent1@test.com / test1234` to log into the mobile app.

### 4.3 Production deploy (Railway)

`railway.json` triggers `npm run build` then `npm run start:prod` on
push to the deployed branch. The mobile app's manifest points to:

```
https://crediiz-core-production.up.railway.app/api/
```

After deploying the backend changes from this batch:

- ✅ No data migration needed — `direction` defaults to `OUTBOUND` for
  legacy records on read; writes get the field on save.
- ✅ Existing mobile installs keep working (DTO field is optional).
- ⚠️ If you want clean test data with the new schema, run `npm run seed`
  against the production DB carefully — **this drops collections**.

### 4.4 Mobile install (Tab A9+)

After the backend is live with these changes:

1. Bump the Android app — already up to date with the contract.
2. Build & install:
   ```bash
   cd /Users/yornel/projects/vortex/calls-agends
   ./gradlew :app:installDebug
   ```
   (or generate an APK via `./gradlew :app:assembleDebug` and sideload).
3. First launch flow:
   - Splash → Login (`agent1@test.com / test1234`).
   - Onboarding gate → grant 6 permissions + dialer role.
   - Clients tab → see assigned queue.
4. Local Room schema is at `version = 2`
   (`fallbackToDestructiveMigration` until v1.0). Fresh install or
   re-install wipes the device DB cleanly.

---

## 5. Process for changing the contract

When you need to add / change a field shared between mobile and backend:

1. **Document the desired change here** as a new sub-section under § 2.
2. **Backend first**: schema → DTO (optional + default) → service mapping → spec test → `npm run build` + `npx jest`.
3. **Mobile side**: domain model → DTO → mapper → repository if needed → `./gradlew :app:compileDebugKotlin`.
4. **Verify both sides build clean** before deploy.
5. **Deploy backend first**, then ship the mobile build. Backwards
   compatibility means old mobile clients still work during the rollout
   window.

The `direction` change in § 2.1 is the worked example — same pattern
applies to any future field.
