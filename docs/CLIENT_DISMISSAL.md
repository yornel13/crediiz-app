# CLIENT_DISMISSAL — Agent-initiated discard of a client

**Status:** 📐 Design approved — pending implementation
**Last updated:** 2026-04-26
**Owner:** mobile (`calls-agends`) + backend (`calls-core`)

---

## 1. What this is

A new **queue-management action** for the agent: arbitrarily mark a
client as "I will not call this person" and remove them from the
active call list, **without** requiring a phone call to have happened.

It is NOT a call outcome. The five existing call outcomes
(`INTERESTED`, `NOT_INTERESTED`, `NO_ANSWER`, `BUSY`, `INVALID_NUMBER`)
all assume a call took place. Dismissal is the missing primitive for
the case where the agent looks at the data and decides the call is
not worth making in the first place — or revises a former INTERESTED
lead and decides to close it without further contact.

---

## 2. Why we need it

Concrete cases the current model cannot express:

| Case | Today's only path | Why it is wrong |
|---|---|---|
| Number is obviously corporate ("0800-XXXX") | Call it and waste the attempt as `INVALID_NUMBER` | Lying about why; pollutes interaction stats |
| Same person uploaded twice | Call them twice or guess one is "invalid" | Both wrong; admin needs the signal too |
| Excel data is clearly malformed (no name, no phone) | Call into nothing | Wastes time; signal lost |
| Existing `INTERESTED` lead no longer worth pursuing | Call them and mark `NOT_INTERESTED` | Forces a fake call; bad user experience |
| Client asked off-record not to be contacted again | No clean path | Compliance risk |

The dismissal action gives all five cases a **truthful, auditable
record** with optional reason, separate from call outcomes.

---

## 3. Behavior contract

### 3.1 Effect on the client

When the agent dismisses a client:

1. Client's `status` changes to `DISMISSED` immediately (locally;
   sync pushes it).
2. Client disappears from **Pendientes** and **Interesados** views.
3. Client appears in **Recientes** with a distinct dismissal badge,
   for 24 h after the dismissal timestamp.
4. Any pending follow-up for that client is **cancelled** (mirrors
   the reassignment behavior in
   `followUpsService.cancelPendingForClient`).
5. The local notification scheduled for that follow-up is cancelled.

### 3.2 Reversibility window — 24 h

For 24 h after dismissal the agent can **undo** from the Recientes
card. Undo restores the client to its `previousStatus` (PENDING or
INTERESTED) and reactivates a previously-cancelled follow-up only if
it has not yet expired.

After 24 h the dismissal is **firm from the agent's side**. The
agent has no UI to reverse it. Reactivation from that point requires
admin action (see § 3.4).

### 3.3 Admin visibility

The admin web panel always sees:
- The DISMISSED status on the client list.
- The full audit trail: who dismissed, when, with what reason.
- A filter "estado = DISMISSED" + group-by-agent for spotting
  agents who over-dismiss.

### 3.4 Admin powers — always

The admin can, **at any time** (no 24 h window):

1. **Reactivate** a dismissed client back to PENDING (default) or
   INTERESTED, **on the same agent**.
2. **Reassign** a dismissed client to **another agent** as PENDING.

Both flows write a new `ClientReassignment` (or equivalent) audit
event. The client's local `ClientDismissal` history is preserved —
the admin's reactivation does not erase the dismissal record, it
overrides it forward.

---

## 4. Data model

### 4.1 New `ClientStatus.DISMISSED`

```ts
// calls-core
export enum ClientStatus {
  PENDING,
  INTERESTED,
  CONVERTED,
  REJECTED,
  INVALID_NUMBER,
  DO_NOT_CALL,
  DISMISSED,    // ← new — agent-initiated removal from active queue
}
```

Why a new status and not reuse `DO_NOT_CALL`:

- `DO_NOT_CALL` is reserved for **regulatory/legal opt-out** lists
  (the contact has explicitly demanded never to be contacted by the
  organization). Conflating it with agent-initiated dismissal would
  break the legal-compliance reading of that flag.
- `DISMISSED` is a **soft, agent-driven** decision. The admin can
  override it. `DO_NOT_CALL` should generally NOT be overridable
  without legal review.

### 4.2 New `ClientDismissal` entity

#### Mobile (Room)

```kotlin
@Entity(tableName = "client_dismissals")
data class ClientDismissalEntity(
    @PrimaryKey val mobileSyncId: String,    // UUID
    val clientId: String,
    val previousStatus: ClientStatus,         // for undo
    val reason: String?,                      // optional, free-form
    val reasonCode: String?,                  // optional preset key
    val dismissedAt: Instant,
    val undone: Boolean,                      // toggled by mobile undo
    val undoneAt: Instant?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
```

#### Backend (Mongo schema)

```ts
@Schema({ timestamps: true })
export class ClientDismissal {
  @Prop({ type: String, required: true, unique: true })
  mobileSyncId!: string;

  @Prop({ type: ObjectId, ref: 'Client', required: true })
  clientId!: Types.ObjectId;

  @Prop({ type: ObjectId, ref: 'Agent', required: true })
  agentId!: Types.ObjectId;

  @Prop({ type: String, enum: ClientStatus, required: true })
  previousStatus!: ClientStatus;

  @Prop({ type: String, default: null })
  reason!: string | null;

  @Prop({ type: String, default: null })
  reasonCode!: string | null;

  @Prop({ type: Date, required: true })
  dismissedAt!: Date;

  @Prop({ type: Boolean, default: false })
  undone!: boolean;

  @Prop({ type: Date, default: null })
  undoneAt!: Date | null;

  @Prop({ type: Date, required: true })
  deviceCreatedAt!: Date;

  createdAt!: Date;
  updatedAt!: Date;
}

ClientDismissalSchema.index({ agentId: 1, dismissedAt: -1 });
ClientDismissalSchema.index({ clientId: 1 });
```

### 4.3 Why a separate entity (not a field on Client)

- **Audit trail:** an event log with timestamp + actor + reason is
  what the admin needs. A boolean field would lose history if a
  client is dismissed → reactivated → dismissed again.
- **Idempotency:** mirrors the existing offline-first pattern (each
  event has a `mobileSyncId` UUID).
- **Reversibility:** `previousStatus` lives on the event, so undo
  restoration is a 1-line operation.
- **Reuse of sync infrastructure:** the existing `SyncRequest`
  envelope just gets a fourth array (`dismissals`).

### 4.4 Reason codes (preset list)

Mobile UI suggests reason codes for fast tapping. Free-form text is
also accepted.

| Code | Spanish label | Use case |
|---|---|---|
| `CORPORATE_NUMBER` | Número corporativo | Phone is a switchboard / 0800 |
| `INVALID_DATA` | Datos errados | Name or phone are obvious garbage |
| `DUPLICATE` | Cliente duplicado | Same person already in another row |
| `OPTOUT` | No quiere ser contactado | Direct request, off-record |
| `OUT_OF_SCOPE` | No aplica al producto | Wrong target audience |
| `OTHER` | Otro motivo | Falls back to free-form text |

The list is **on the mobile side only** for v1.0. The reason code
is sent as a string; backend stores it without enforcing an enum so
the list can grow without backend deploys.

---

## 5. Sync flow

### 5.1 Outbound (mobile → server)

Extend `SyncRequest` with a fourth array:

```ts
// calls-core
class SyncRequestDto {
  @IsOptional @IsArray interactions?: SyncInteractionDto[];
  @IsOptional @IsArray notes?: SyncNoteDto[];
  @IsOptional @IsArray followUps?: SyncFollowUpDto[];
  @IsOptional @IsArray completedFollowUps?: SyncCompletedFollowUpDto[];
  @IsOptional @IsArray dismissals?: SyncDismissalDto[];   // ← new
}

class SyncDismissalDto {
  @IsString mobileSyncId!: string;
  @IsString clientId!: string;
  @IsEnum(ClientStatus) previousStatus!: ClientStatus;
  @IsOptional @IsString reason?: string;
  @IsOptional @IsString reasonCode?: string;
  @IsDateString dismissedAt!: string;
  @IsBoolean undone!: boolean;
  @IsOptional @IsDateString undoneAt?: string;
  @IsDateString deviceCreatedAt!: string;
}
```

### 5.2 Server processing

`sync.service.processDismissals` (new):

```ts
for (const item of dismissals) {
  try {
    // Idempotent insert by mobileSyncId
    await this.dismissalsService.upsert({
      mobileSyncId: item.mobileSyncId,
      clientId: item.clientId,
      agentId,
      previousStatus: item.previousStatus,
      reason: item.reason ?? null,
      reasonCode: item.reasonCode ?? null,
      dismissedAt: new Date(item.dismissedAt),
      undone: item.undone,
      undoneAt: item.undoneAt ? new Date(item.undoneAt) : null,
      deviceCreatedAt: new Date(item.deviceCreatedAt),
    });

    // CRITICAL: status mutation OUTSIDE the duplicate-key catch
    // (lesson from KI-06). Idempotent — re-applying same status is a no-op.
    if (item.undone) {
      await this.clientsService.setStatus(item.clientId, item.previousStatus);
      await this.followUpsService.reactivatePendingForClient(item.clientId, agentId);
    } else {
      await this.clientsService.setStatus(item.clientId, ClientStatus.DISMISSED);
      await this.followUpsService.cancelPendingForClient(
        item.clientId,
        agentId,
        'Client dismissed by agent',
      );
    }
    results.push({ mobileSyncId: item.mobileSyncId, status: 'created' });
  } catch (err) {
    /* error handling identical to processInteractions */
  }
}
```

### 5.3 Inbound — admin reactivation flows down to mobile

When admin reactivates or reassigns a dismissed client, the next
`refreshAssigned(PENDING)` (or INTERESTED) returns the client. The
mobile's `replaceAllByStatus` inserts the row. The local
`ClientDismissalEntity` for that client stays in the
`client_dismissals` table as historical record but the client itself
is back in the active list.

The mobile does **not** need a "dismissals refresh" endpoint for
v1.0 — the client status sync is enough to reflect admin overrides.

---

## 6. Mobile UX

### 6.1 Entry points (where the agent starts a dismissal)

| Surface | UI element | Why |
|---|---|---|
| Pendientes card | `⋯` overflow menu → "Descartar cliente" | Quick action without entering Pre-Call |
| Pre-Call screen | secondary button below "Llamar" | Agent looked at the data and decided not to call |
| Interesados card | `⋯` overflow menu → "Descartar cliente" | Lead has gone cold |

**Not in Post-Call.** Post-Call is for call outcomes. The five
buttons there cover any "call happened" path. Dismissal is the
"call did not / will not happen" path.

### 6.2 Confirmation sheet

```
┌─────────────────────────────────────────────┐
│  Descartar a Maria López                    │
│                                             │
│  Este cliente saldrá de tu lista de         │
│  llamadas. Lo verás en Recientes por 24 h   │
│  por si quieres deshacer.                   │
│                                             │
│  Razón (opcional)                           │
│  ╭─────────────╮ ╭─────────────╮            │
│  │ Corporativo │ │   Errado    │            │
│  ╰─────────────╯ ╰─────────────╯            │
│  ╭─────────────╮ ╭─────────────╮            │
│  │ Duplicado   │ │   Opt-out   │            │
│  ╰─────────────╯ ╰─────────────╯            │
│  ╭─────────────╮ ╭─────────────╮            │
│  │ No aplica   │ │    Otro…    │            │
│  ╰─────────────╯ ╰─────────────╯            │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ (campo libre opcional, 0 / 200)     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│       [ Cancelar ]   [ Descartar ]          │
└─────────────────────────────────────────────┘
```

- Six chips for the preset reason codes; tapping one selects it.
- Tapping "Otro…" opens the free-form field.
- Free-form field is always available below the chips for
  additional context (combinable with a preset code).
- "Descartar" is enabled regardless of reason — friction-free.

### 6.3 Recientes card variant — dismissed

```
┌─────────────────────────────────────────────────────┐
│  ⨯  Maria López                       hace 12 min  │
│     DESCARTADO  · "número corporativo"              │
│     [↩ Deshacer descarte]                           │
└─────────────────────────────────────────────────────┘
```

- `⨯` badge in `outline` color, distinct from outcome circles.
- "DESCARTADO" label in uppercase `labelMedium`.
- Reason in italic if present.
- Single action: **Deshacer descarte**. Visible only inside the
  24 h window.
- Tap on the card body opens the read-only client detail sheet
  (same one used for Recientes-call cards).

### 6.4 Undo flow

Tap "Deshacer descarte":

1. Mobile updates the local `ClientDismissalEntity` setting
   `undone = true, undoneAt = now, syncStatus = PENDING`.
2. Mobile sets the local `client.status` back to `previousStatus`.
3. The client immediately reappears in Pendientes (or Interesados).
4. The Recientes card disappears (the dismissal is "no longer the
   most recent state").
5. Sync pushes the undo as a new dismissal payload.

If the original dismissal had not yet synced when undo is tapped,
mobile **collapses** both events into a single sync payload (a
dismissal with `undone = true`). Saves a round trip.

### 6.5 Empty-state copy update

The Recientes empty state stays the same; the Pendientes empty
state changes only when the agent has dismissed everything:

> "Has descartado a todos tus pendientes. Pídele al admin que te
> asigne más, o revisa Recientes para deshacer si te
> arrepentiste."

---

## 7. Admin web panel — what it gets

> Implementation lives in the `calls-core` admin web panel, not in
> mobile. Documented here so both sides agree on the contract.

### 7.1 New views / filters

- Client list filter: `status = DISMISSED`.
- New columns when filter is active: "Dismissed by", "When",
  "Reason" (final UI labels TBD by the web-panel team — these are
  the data fields they need to expose).
- Client detail page: full timeline of dismissals + reactivations.

### 7.2 New admin actions

- **Reactivate** — sets the client back to PENDING (or INTERESTED),
  same agent. Records an audit event of type
  `ADMIN_REACTIVATION`.
- **Reassign to another agent** — changes `assignedTo` AND resets
  status to PENDING. Records audit event of type
  `ADMIN_REASSIGNMENT_AFTER_DISMISSAL`.

Both actions are **always available**, not constrained to the 24 h
window. The admin's authority is unconditional (this was the
explicit decision from product).

### 7.3 Reports the admin gets for free

- % of clients dismissed per agent, per month.
- Reason-code distribution (top 5).
- Undo rate within the 24 h window (a proxy signal for the
  agent's dismissal-criterion quality).

These reports are **out of scope for v1.0** — the data is
collected so the admin can run the reports later when prioritized.

---

## 8. Edge cases

| Case | Behavior |
|---|---|
| Agent dismisses, then loses connection for >24 h | Local: client out of Pendientes, in Recientes (dismissed); after 24 h disappears from Recientes locally. Sync upon reconnect: server gets the event and applies status. |
| Agent dismisses, then admin reactivates before sync arrives | Sync arrives, server sees the client is now PENDING again with a different `updatedAt` than the dismissal claims. **Resolution:** the dismissal event is still recorded in audit (it happened), but the status is NOT changed (admin action is newer wins). Mobile's next refresh re-syncs the active status. |
| Same client dismissed twice (double-tap) | First insert succeeds; second hits `mobileSyncId` unique. `processDismissals` catch returns `'duplicate'`, status mutation already applied — idempotent. |
| Agent dismisses a client whose follow-up is in 5 minutes | Follow-up cancelled, local notification cancelled. If undo within 24 h, follow-up reactivated **only if** `scheduledAt > now`. Past-due follow-ups stay cancelled. |
| Admin reactivates → reassigns to other agent same minute | Two audit events. Final state: client in agent B's PENDING list. Agent A's mobile sees client disappear from their `client_dismissals`-driven Recientes view (because the client is no longer assigned to A — their refreshed list excludes it). |
| Two agents have the same client in dismissed history (after admin reassign) | Each dismissal event is tied to its `agentId`. Admin sees both rows in the audit trail. |

---

## 9. Files to be touched

### 9.1 Backend (`calls-core`)

**New:**
- `src/dismissals/schemas/client-dismissal.schema.ts`
- `src/dismissals/dismissals.service.ts`
- `src/dismissals/dismissals.module.ts`
- `src/sync/dto/sync-dismissal.dto.ts`

**Modified:**
- `src/common/enums/client-status.enum.ts` — add `DISMISSED`
- `src/sync/sync.service.ts` — add `processDismissals`
- `src/sync/dto/sync-request.dto.ts` — add `dismissals` array
- `src/clients/clients.service.ts` — add `setStatus(id, status)`
  helper if it does not exist
- `src/follow-ups/follow-ups.service.ts` — add
  `reactivatePendingForClient(clientId, agentId)` for undo
- `src/clients/clients.controller.ts` — admin endpoints
  `POST /clients/:id/reactivate` and reuse `assign` for reassignment

### 9.2 Mobile (`calls-agends`)

**New:**
- `data/local/entity/ClientDismissalEntity.kt`
- `data/local/db/ClientDismissalDao.kt`
- `data/remote/dto/SyncDismissalDto.kt`
- `data/repository/ClientDismissalRepositoryImpl.kt`
- `domain/repository/ClientDismissalRepository.kt`
- `domain/model/ClientDismissal.kt`
- `presentation/clients/components/DismissClientSheet.kt`
- `presentation/clients/components/RecentDismissalCard.kt`
- `common/enums/DismissalReasonCode.kt` (with localized labels)

**Modified:**
- `common/enums/ClientStatus.kt` — add `DISMISSED`
- `data/local/db/AppDatabase.kt` — add `client_dismissals` table,
  bump schema version (still using `fallbackToDestructiveMigration`
  pre-v1.0)
- `data/sync/SyncManager.kt` — collect dismissals into
  `pendingSync()` and into `SyncRequest`
- `data/repository/ClientRepositoryImpl.kt` — local action
  `dismissClient(clientId, reason, reasonCode)` and `undoDismissal`
- `presentation/clients/ClientsViewModel.kt` — wire the action +
  surface dismissals into Recientes flow
- `presentation/clients/ClientsScreen.kt` — overflow menus on cards
- `presentation/precall/PreCallScreen.kt` — secondary "Descartar"
  button

### 9.3 Documentation

**Modified:**
- `docs/MANUAL_USUARIO.md` — new section "Descartar cliente"
- `docs/CLIENTS_TAB_REDESIGN.md` — Recientes now sources from
  calls AND dismissals
- `docs/BACKEND_COORDINATION.md` — new endpoint and enum
- `docs/AGENT_UX_BACKLOG.md` — close the implicit "discard a
  client" gap if it exists, or add this entry

---

## 10. Phasing

| Phase | Scope | Ships in |
|---|---|---|
| **D1** | Backend: enum + entity + sync + admin reactivate endpoint | v1.0 |
| **D2** | Mobile: dismissal action from Pendientes overflow + sheet + Recientes card variant + undo | v1.0 |
| **D3** | Mobile: dismissal entry from Pre-Call and Interesados overflow | v1.0 |
| **D4** | Admin web: filter, audit trail UI, reactivate button, reassign-to-other-agent flow for dismissed | v1.0 (web panel) |
| **D5** | Admin reports: % dismissed by agent, top reasons, undo rate | v1.1 |

D1–D4 ship together as the dismissal feature. D5 is data already
captured but not yet visualized — pure web-panel work.

---

## 11. Open questions

1. **Reason codes — final list?** § 4.4 has a starter set. Confirm
   with operations before implementing the chips.
2. **Free-form reason — character limit?** Proposing 200. Adjust
   if needed for compliance reasons.
3. **Should Recientes show admin reactivations as cards too?** "El
   admin reactivó a Maria López hace 5 min" so the agent sees what
   happened to their dismissals. Recommendation: not in v1.0; the
   client simply reappears in Pendientes with a flash animation.
   Agents can be told this in onboarding.
4. **Notifications when admin reactivates?** Push notification
   "Admin te reactivó 3 clientes"? Not in v1.0; keep the inbox
   silent until we have user feedback.
