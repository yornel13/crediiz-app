# OVERVIEW — Product & Agent Workflow

## 0. Product boundary (LOCKED RULE)

> **This Android app is for agents only.** Administrators / owners never use
> this app. All admin-facing features (client upload, agent CRUD,
> cross-agent dashboards, daily/weekly/monthly reports, follow-up
> supervision) live exclusively in the **Angular web panel** in the
> [`calls-core`](../../calls-core/) repo.

**Consequences — applied to every scope decision:**

- No admin UI of any kind ships in the mobile app.
- Dashboards, reports, and cross-agent analytics are **web panel only**.
- The mobile app's contribution to reporting is **emitting raw data**
  (interactions, notes, follow-ups) via sync. The backend aggregates.
- Personal-activity surfaces for the agent themselves (e.g., "my calls
  today") are acceptable UX but are **not** admin reports. These are
  v1.1+ per the current MVP cut; they do not ship in v1.0.

If a feature request would put admin functionality in the mobile app,
reject the scope and route it to `calls-core`.

---

## 1. Product context

Panama Calls is a **CRM for outbound call campaigns** targeting Panamanian
clients. The business model is lead acquisition for financial products
(loans, mortgages, etc.).

Two user groups:

- **Admin / Owner** — uses a web panel (Angular, separate repo). Uploads
  client lists from Excel, creates agents, assigns clients to agents, reads
  dashboards and supervises agendas.
- **Agent** — uses **this Android tablet app**. Opens their assigned client
  queue, calls clients, records outcomes, schedules follow-ups.

This doc covers only the agent side. For the owner side and backend internals
see `calls-core/docs/`.

---

## 2. Agent persona

- Works on a **Samsung Galaxy Tab A9+** at a desk or in the field.
- Places **many short calls per day** (50–200 expected in production).
- Frequently operates under poor connectivity — the app **must work offline**
  and sync later.
- Is not a power user; UI must be large, obvious, error-tolerant.
- Spanish-speaking (UI strings should eventually be localized; currently hardcoded
  English in most screens — acceptable for MVP, flag for post-MVP).

---

## 3. Daily workflow (happy path)

```
1. Agent opens the app.
      │
      ▼
2. Login screen (email + password).
      │  POST /api/auth/login  →  JWT stored in encrypted DataStore.
      ▼
3. Home hub with 3 tabs:
   ┌─────────┬─────────┬──────────┐
   │ Clients │ Agenda  │ Settings │
   └─────────┴─────────┴──────────┘

4. CLIENTS TAB
   ├── Reads PENDING clients assigned to this agent from Room.
   ├── Shows name, phone, callAttempts.
   ├── Search bar (local filter).
   └── FAB: "Start Auto-Call" (sequential auto-dial mode).

5. Agent taps a client → Pre-Call Screen
   ├── Client info card (name, phone, extraData like city/loanType).
   ├── Previous interaction history.
   └── "Call" button.

6. Call placed via Telecom (native dialer).
   │
   ▼
7. IN-CALL SCREEN (overlay or on top of native dialer UI)
   ├── Live timer.
   ├── Notes text field (stays open during the whole call).
   ├── Mute / speaker controls.
   └── Waits for call end event from TelecomManager.

8. POST-CALL SCREEN
   ├── Pre-filled note from in-call.
   ├── Outcome buttons: Interested, Not Interested, No Answer, Busy,
   │   Invalid Number.
   ├── If Interested → follow-up form (date, time, reason).
   └── "Save" persists to Room and triggers immediate sync attempt.

9. AUTO-CALL MODE (optional toggle in Settings)
   ├── After Post-Call save, countdown 5s → auto-advance to next client.
   └── Session summary at the end (calls made, interested count).
```

---

## 4. Follow-up / agenda flow

1. Agent marks a past call as Interested → schedules a follow-up.
2. Follow-up is stored locally and queued for sync.
3. The **Agenda tab** groups follow-ups by date (Today / Tomorrow / This
   Week / Later) directly from the Room DB.
4. A **local notification** (AlarmManager, not yet implemented) fires 5
   minutes before the scheduled time.
5. Tapping the notification or the agenda entry opens Pre-Call for that
   client. After the call, the same outcome flow applies; if rescheduled,
   the old follow-up is marked COMPLETED and a new one is created.

---

## 5. Client status lifecycle (from the agent's point of view)

| Status | How the agent causes it | Next visibility |
|---|---|---|
| `PENDING` | Default on assignment. | Shown in Clients tab queue. |
| `INTERESTED` | Outcome = Interested. | Moves off queue; appears via follow-up in Agenda. |
| `REJECTED` | Outcome = Not Interested. | Removed from queue. |
| `INVALID_NUMBER` | Outcome = Invalid Number. | Removed from queue; admin reviews. |
| `CONVERTED` | **Not set by agent** — admin marks in the web panel. | Removed from queue. |
| `DO_NOT_CALL` | **Not set by agent** — admin only. | Removed from queue. |

Counters like `callAttempts` and `lastOutcome` are updated server-side
on sync.

---

## 6. Sync semantics (crucial — read carefully)

- The device **only creates** interactions, notes, and follow-ups. It never
  updates server records directly. Status transitions happen on the backend
  based on the outcome the device reports.
- Every created record has a **`mobileSyncId` UUID** generated on-device.
  The backend uses unique indexes to deduplicate. **Resending is always
  safe.**
- Sync is attempted:
  - **Immediately** after saving a Post-Call.
  - **Periodically** via WorkManager (every 15–30 minutes).
  - **On reconnect** via `ConnectivityObserver`.
- After a successful sync, the app re-fetches assigned clients and
  follow-ups to reflect server-side changes (reassignments, admin-set
  status, etc.).

---

## 7. Out of scope (MVP)

Explicitly **not** part of the agent app:

- Creating or editing clients.
- Editing past notes (notes are append-only from the device).
- Client-to-client transfer (only admin can reassign).
- iOS / phone form factors.
- Call recording.
- Server-push notifications (only local notifications for follow-up
  reminders).
- Real-time chat or messaging.

---

## 7.5. Known sync/refresh defects (under review)

⚠️ Five defects in the offline-sync + data-refresh layer are documented
in [`KNOWN_ISSUES.md`](./KNOWN_ISSUES.md):

- **KI-01** `replaceAll` is destructive and overwrites pending local changes.
- **KI-02** Race between two `refreshAssigned` calls empties Clients tab.
- **KI-03** No "in-call" guard for data refresh.
- **KI-04** Unassigned client leaves orphan local records.
- **KI-05** Unverified CASCADE behavior on Client FK.

Read that doc before extending or modifying anything in
`data/sync/` or `data/local/db/ClientDao.kt`.

## 8. Common pitfalls / things to not reinvent

- Don't call server endpoints for data that Room already holds — the UI
  should **always** read from Room. The network layer only writes (sync)
  or re-hydrates Room after sync.
- Don't treat `assignedTo` as a client-editable field on the device. It's
  read-only from the agent's side.
- Don't assume the device is online. Every write must land in Room first.
- Don't swallow `CallOutcome` enums. They are exhaustive — add new
  outcomes only after updating the backend enum and the mapping in
  `calls-core/src/common/enums/`.
