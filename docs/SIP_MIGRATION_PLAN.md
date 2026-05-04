# SIP_MIGRATION_PLAN — Total replacement of Telecom dialer with SIP/Linphone

**Owner:** Yornel
**Estimate:** ~83 working hours / 2.1 calendar weeks at 40h/week
**Branch:** `main` (no feature branch — work lands directly)
**Companion docs:** [`SIP_VOSELIA_CONFIG.md`](./SIP_VOSELIA_CONFIG.md),
[`TELECOM_ARCHITECTURE.md`](./TELECOM_ARCHITECTURE.md) (now SUPERSEDED)
**Status:** Awaiting GO. Doc-only phase — no code yet.

---

## 1. Goal — Option A, total replacement

Replace the current Android Telecom-based PSTN dialer with a
**SIP/VoIP outbound** client built on the **Linphone Android SDK**,
registered against **Voselia** at `cpbxa.vozelia.com.pa`.

Three things change:

1. **Call carrier** — cellular PSTN → SIP over the internet.
2. **OS integration** — `RoleManager.ROLE_DIALER` + `ConnectionService`
   + `InCallService` are **deleted**. The app no longer is the default
   dialer because there is no PSTN traffic to intercept.
3. **Foreground service type** — `phone_call` → `microphone` (Android 14+
   requirement when capturing mic in background).

Three things **don't** change:

- The custom in-call UI (`InCallActivity` + `InCallScreen`).
- The persistence layer (`InteractionEntity`, `NoteEntity`,
  `MissedCallEntity`, sync pipeline).
- The agent UX (tap "Call" on `PreCallScreen` → in-call screen → post-call).

**Done when:**

- An agent can place an outbound SIP call from `calls-agends` and hear
  bidirectional audio on **WiFi and 4G**.
- The call survives **app backgrounding** on a Galaxy Tab A9+
  running Android 15.
- DTMF tones reach a third-party IVR.
- Hangup is clean. The call lands in `InteractionEntity` + sync queue
  exactly like before.
- The Telecom layer (`telecom/CallsConnectionService.kt`,
  `telecom/CallsInCallService.kt`, `telecom/DisconnectCauseMapper.kt`)
  is gone, the manifest is clean, and `TELECOM_ARCHITECTURE.md` is
  marked `SUPERSEDED`.

Anything beyond that is out of scope for v1 (see §10).

---

## 2. Inventory — what gets touched

This is the **real** scope, measured by reading the codebase. Numbers
are wc-l counts on 2026-05-03.

### 2.1 Files to delete (3 files, 201 lines)

| File                                                 | Lines | Reason                                        |
|------------------------------------------------------|-------|-----------------------------------------------|
| `telecom/CallsConnectionService.kt`                  | 86    | No PSTN → no `ConnectionService` needed       |
| `telecom/CallsInCallService.kt`                      | 57    | No PSTN → no `InCallService` needed           |
| `telecom/DisconnectCauseMapper.kt`                   | 58    | Telecom-specific; Linphone has its own errors |

### 2.2 Files to refactor (1 file, 367 lines → ~400 lines)

| File                          | Current responsibility                                      | After                                                                 |
|-------------------------------|-------------------------------------------------------------|-----------------------------------------------------------------------|
| `telecom/CallManager.kt`      | Wraps `TelecomManager.placeCall()` + `Call.Callback`        | Wraps `LinphoneCoreManager.placeCall()` + Linphone `Core` listeners.  |
|                               | Persistence (Interaction, Note, MissedCall, sync) **kept**.| Identical persistence path — call-engine-agnostic.                    |

The class will likely be **renamed** to neutral `CallController` and
moved out of `telecom/` (to `domain/call/` or `data/call/`). Decision
finalized at code-write time.

### 2.3 Files to keep as-is, import path only

| File                                       | Lines | Note                                                        |
|--------------------------------------------|-------|-------------------------------------------------------------|
| `telecom/model/CallUiState.kt`             | 34    | Sealed class is engine-agnostic. Move to `domain/call/model/`. |

### 2.4 Files to modify (mostly imports + small flow changes)

| File                                                            | Lines | Change                                                                                                  |
|-----------------------------------------------------------------|-------|---------------------------------------------------------------------------------------------------------|
| `data/sync/InCallGate.kt`                                       | ~50   | Update import; logic intact (still listens to `callState`).                                              |
| `presentation/navigation/CallNavigationViewModel.kt`            | ~50   | Update import; logic intact.                                                                             |
| `presentation/navigation/AppNavGraph.kt`                        | ~50   | Update import; doc-comment cleanup.                                                                      |
| `presentation/incall/InCallActivity.kt`                         | 34    | Launched by SIP `CallSession` start (not by `InCallService.onCallAdded`). Doc-comment update.            |
| `presentation/incall/InCallScreen.kt`                           | 511   | **No functional change** — consumes the same `CallUiState`. UI untouched.                                |
| `presentation/incall/InCallViewModel.kt`                        | 37    | Update import.                                                                                           |
| `presentation/precall/PreCallViewModel.kt`                      | 245   | Update import; `startCall()` flow identical.                                                             |
| `presentation/precall/PreCallScreen.kt`                         | 1197  | **⚠ over the 1000-line cap.** Do not grow. Pre-existing issue, surface as a follow-up task post-cutover. |
| `presentation/autocall/AutoCallOrchestrator.kt`                 | 196   | Update import; logic intact.                                                                             |
| `presentation/onboarding/OnboardingActivity.kt`                 | 190   | **Remove** the `RoleManager.ROLE_DIALER` request flow. **Add** `RECORD_AUDIO` runtime grant flow.        |
| `presentation/onboarding/OnboardingGate.kt`                     | 74    | **Remove** `RoleManager.isRoleHeld(ROLE_DIALER)` check. Add `RECORD_AUDIO` + battery-opt checks.         |
| `presentation/onboarding/OnboardingScreen.kt`                   | 277   | Update copy: "default dialer" → "microphone access" + "battery exemption". Re-arrange permission rows.   |
| `presentation/onboarding/OnboardingViewModel.kt`                | 58    | Drop dialer-role state; add SIP-permissions state.                                                       |
| `data/local/entity/InteractionEntity.kt`                        | ~60   | No functional change. Doc-comment cleanup if it references Telecom.                                      |
| `AndroidManifest.xml`                                           | ~120  | See §2.5.                                                                                                |
| `app/build.gradle.kts`                                          | ~110  | Add Linphone dep, ABI filter `arm64-v8a`, Buildconfig fields for SIP creds (Phase A — hardcoded).        |
| `gradle/libs.versions.toml`                                     | —     | Add Linphone version + library entry.                                                                    |
| `local.properties`                                              | —     | (gitignored) Add `sip.user`, `sip.password`, `sip.server` for Phase A.                                   |

### 2.5 AndroidManifest.xml — diff summary

**Remove (~30 lines):**

- `<uses-permission CALL_PHONE>`, `ANSWER_PHONE_CALLS`, `READ_PHONE_STATE`,
  `MANAGE_OWN_CALLS`, `FOREGROUND_SERVICE_PHONE_CALL`.
- `<service .telecom.CallsConnectionService>` block (10 lines).
- `<service .telecom.CallsInCallService>` block (16 lines).
- `<intent-filter android.intent.action.DIAL>` blocks on `MainActivity`
  (8 lines) — kept the LAUNCHER one.

**Add (~10 lines):**

- `<uses-permission RECORD_AUDIO>`.
- `<uses-permission FOREGROUND_SERVICE_MICROPHONE>` (Android 14+).
- `<service .data.sip.SipCallForegroundService android:foregroundServiceType="microphone">`.

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `POST_NOTIFICATIONS` already
declared — kept.

### 2.6 Files to create (~10 files, ~600–800 lines total)

| File                                            | Lines (est.) | Responsibility                                                                                       |
|-------------------------------------------------|--------------|------------------------------------------------------------------------------------------------------|
| `data/sip/SipConfig.kt`                         | ~30          | Immutable config: server, user, password, transport, SRTP policy. Read from `BuildConfig`.          |
| `data/sip/auth/SipCredentialsProvider.kt`       | ~50          | Indirection. **Phase A** returns BuildConfig values. **Phase B** calls backend on session start.    |
| `data/sip/LinphoneCoreManager.kt`               | ~250         | Singleton. Owns `Core` + `iterate()` thread. Exposes registration `StateFlow`. Emits call sessions. |
| `data/sip/CallSession.kt`                       | ~120         | Wraps a Linphone `Call`. Mirrors `Call.State` to `CallUiState`. Mute/speaker/hangup APIs.           |
| `data/sip/SipCallForegroundService.kt`          | ~100         | `foregroundServiceType="microphone"`. Started on call placed, stopped on call end.                  |
| `data/sip/audio/SipAudioRouter.kt`              | ~80          | `AudioManager.setMode(IN_COMMUNICATION)` + speaker/earpiece via `setCommunicationDevice()`.         |
| `data/sip/SipLogger.kt`                         | ~50          | Routes Linphone `Factory.loggingService` to a rotating file at `filesDir/sip-logs/`.                |
| `data/sip/di/SipModule.kt`                      | ~40          | Hilt: `@Provides` for `LinphoneCoreManager`, `SipConfig`, `SipCredentialsProvider`.                 |
| `domain/call/model/CallUiState.kt`              | 34           | (moved from `telecom/model/`).                                                                       |
| `domain/call/CallController.kt` (renamed `CallManager`) | ~400 | (refactored from `telecom/CallManager.kt`).                                                          |

**Total churn:**
- Deleted: ~201 lines.
- Modified: ~400 lines effective change across 17 files (most are import-only).
- Added: ~700 lines new.

---

## 3. Credentials strategy — two phases

Pragmatic now, correct later. The user explicitly authorized the
hardcoded shortcut to unblock progress.

### Phase A — Hardcoded (this migration)

- `sip.user`, `sip.password`, `sip.server` live in `local.properties`
  (gitignored).
- `app/build.gradle.kts` reads them at build time and exposes via
  `BuildConfig.SIP_USER`, `SIP_PASSWORD`, `SIP_SERVER`.
- `SipCredentialsProvider` in Phase A is a single-line
  `BuildConfig`-backed implementation.
- **All four agents would technically use the same APK build with the
  same hardcoded credentials in v1 dev/test. This is not deployable to
  multiple agents in production.**

**Why this is acceptable as Phase A**: it lets us validate the SIP
plumbing end-to-end (M1–M6 milestones) without coupling the work to
the backend roadmap. The validation is the same regardless of where
the credentials come from.

### Phase B — Backend-issued (post-MVP, separate epic)

- Admin assigns a `sip.account` to each agent in the web panel
  (calls-core).
- On login, the mobile app fetches the agent's SIP credentials from a
  new backend endpoint (e.g., `GET /api/agents/me/sip-account`).
- Credentials cached in `EncryptedSharedPreferences`. Never in plain
  `BuildConfig`.
- `SipCredentialsProvider` Phase B implementation hits the API on first
  registration and refreshes on 401 from the SBC.
- Backend coordination needed: schema, endpoint, admin UI in
  `calls-core`. **Not part of this plan.** Tracked separately in
  [`PENDING_BACKEND_WORK.md`](./PENDING_BACKEND_WORK.md) (TBD entry).

The interface between Phase A and Phase B is `SipCredentialsProvider`.
Phase B replaces the implementation, no other code changes.

---

## 4. Working assumptions for unknowns

Eleven `READY` parameters come from production Sipnetic inspection —
see [`SIP_VOSELIA_CONFIG.md` §1](./SIP_VOSELIA_CONFIG.md). The remaining
parameters enter the code as `// TODO(VOSELIA-CONFIRM)` constants:

| Parameter      | Default in code                          | Empirical validation                |
|----------------|------------------------------------------|-------------------------------------|
| STUN           | Disabled                                 | Milestones M3 / M4                  |
| TURN           | Disabled                                 | M4                                  |
| ICE            | Disabled                                 | M3                                  |
| Expires        | 3600 s (Linphone default)                | Production observation              |
| Keep-alive UDP | 30 s                                     | Long-call test (Day 9)              |
| rport          | Enabled                                  | M3 (look at REGISTER `Via:`)        |
| DTMF method    | RFC 2833 (telephone-event)               | M5                                  |
| Auth user      | = SIP user                               | M1                                  |
| Outbound proxy | None (registrar = proxy)                 | M1                                  |

If any prove wrong during the milestones, the fix is a constant change
— no architectural rework.

---

## 5. Test asset

- **One dedicated Voselia agent account** ("Vanesa", SIP user
  `201-11435`, server `cpbxa.vozelia.com.pa`). Operations confirmed
  unrestricted use for testing — no risk of disturbing a real agent.
  Credentials in `local.properties`.
- **One Galaxy Tab A9+ with Android 15** for development. Production
  parity from day one — no emulator-only flows.
- **Three call-target numbers**:
  1. Landline / personal mobile reachable (e.g., `6503-2939`) —
     bidirectional audio test.
  2. An IVR (operator's customer-service menu) — DTMF validation.
  3. A long-running test target (callback to self) — ≥1h stability.

---

## 6. Phase breakdown

Hours assume one Senior Android dev, no parallelization. Each phase
ends with a concrete deliverable and a binary pass/fail check.

### Phase 0 — Pre-work (1h, before Day 1)

- [ ] Confirm Linphone SDK GPLv3 acceptable for internal sideload —
      already verified by user. No further sign-off.
- [ ] Test-agent credentials placed in `local.properties` (Phase A).
- [ ] Working tree clean on `main` (commit pending changes from
      `git status` first or set them aside).

**Deliverable:** Clean `main`, credentials in place.

---

### Phase 1 — Setup & smoke test (5h, Day 1)

- [ ] Add Linphone SDK to `gradle/libs.versions.toml` and
      `app/build.gradle.kts`. ABI filter `arm64-v8a` only.
- [ ] `BuildConfig` fields for `SIP_SERVER`, `SIP_USER`, `SIP_PASSWORD`
      sourced from `local.properties`.
- [ ] Skeleton: `SipConfig.kt`, `SipCredentialsProvider.kt` (Phase A
      impl), `LinphoneCoreManager.kt` (init + `iterate()` thread + a
      bare `register()` call), `SipModule.kt` (Hilt).
- [ ] Throwaway debug button on `OnboardingScreen` (or a hidden tap
      target) that fires `LinphoneCoreManager.register()` and logs the
      `RegistrationState` flow.

**Deliverable:** APK that registers — or fails with a clear log line.
**Validation gate:** **Milestone M1** — see §7.

---

### Phase 2 — Telecom demolition (3h, Day 2 morning)

The order is intentional: tear down before building up, so we never
have two parallel call engines.

- [ ] Delete `telecom/CallsConnectionService.kt`,
      `telecom/CallsInCallService.kt`,
      `telecom/DisconnectCauseMapper.kt`.
- [ ] Move `telecom/model/CallUiState.kt` →
      `domain/call/model/CallUiState.kt`. Update all imports
      (10 files, mechanical).
- [ ] Manifest cleanup per §2.5.
- [ ] `OnboardingGate` and `OnboardingActivity`: drop `ROLE_DIALER`
      checks and the role-request `Intent`. Keep the visual skeleton
      so the user still sees the onboarding flow.
- [ ] Build, run, confirm the app launches without crashes. Calls won't
      work yet — that's expected. Existing PSTN flow is dead weight at
      this point but doesn't error out because nobody invokes it.

**Deliverable:** Clean tree, app boots, Telecom is gone.
**Validation gate:** `./gradlew app:assembleDebug` passes; manual app
launch reaches `MainActivity` without a crash.

---

### Phase 3 — SIP core layer (16h, Day 2 afternoon → Day 4)

Implements the production-grade abstraction the rest of the app uses.

- [ ] **3.1** `LinphoneCoreManager` (singleton). Owns `Core`, runs
      `iterate()` on a `HandlerThread`. `start()` / `stop()`.
      Exposes `RegistrationState` as `StateFlow`. (5h)
- [ ] **3.2** Account configuration: UDP preferred (TCP/TLS fallback),
      SRTP/SDES mandatory, codec list per Sipnetic priority, AGC + EC
      on, RTP port range 16384–65535. (4h)
- [ ] **3.3** Registration with exponential backoff
      (1 → 2 → 4 → 8 → 60s cap). (3h)
- [ ] **3.4** `CallSession` — wraps Linphone `Call`. Maps `Call.State`
      to `CallUiState` (`Idle/Dialing/Ringing/Active/Disconnected`).
      `mute()`, `setSpeaker()`, `disconnect()`, `dtmf()`. (4h)

**Deliverable:** `LinphoneCoreManager` registers, `CallSession`
places a call, exposes state as flows, tears down cleanly.
**Validation gate:** **M2** at end of Day 2; **M3 (WiFi)** by end of Day 4.

---

### Phase 4 — Call orchestration refactor (8h, Day 5)

Replaces `telecom/CallManager.kt` (PSTN) with `domain/call/CallController.kt`
(SIP). Persistence and sync paths preserved.

- [ ] **4.1** Create `domain/call/CallController.kt` mirroring the
      public surface of `CallManager` (state flows, `startCall`,
      `mute`, `setSpeaker`, `disconnect`, `lastEndedCall`,
      `consumeLastEndedCall`). (4h)
- [ ] **4.2** Wire `CallController` to `CallSession`: collect
      `CallUiState` flow, drive the foreground service start/stop, run
      persistence + sync on call end. (3h)
- [ ] **4.3** Delete old `telecom/CallManager.kt`. Update Hilt module +
      consumer imports across the 8 files in §2.4. (1h)

**Deliverable:** Existing PreCall → InCall → PostCall flow works again,
now over SIP.
**Validation gate:** Manual run-through with the test agent — full
agent flow ends in `PostCallScreen` with a persisted `InteractionEntity`.

---

### Phase 5 — Foreground service (10h, Day 6)

- [ ] **5.1** `SipCallForegroundService` with
      `foregroundServiceType="microphone"`. Started on
      `CallSession.start()`, stopped on `Call.State.End`. Notification
      with hangup action. (4h)
- [ ] **5.2** Runtime permission flow (in `OnboardingActivity` and as a
      pre-call guard): `RECORD_AUDIO`,
      `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`. (3h)
- [ ] **5.3** `AudioFocusRequest(USAGE_VOICE_COMMUNICATION)` +
      `WakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK)`. (3h)

**Deliverable:** Calls survive app minimization. Mic is not silenced
by Android 15 policy.
**Validation gate:** **M6** runs at end of phase.

---

### Phase 6 — Audio routing (4h, Day 7 morning)

- [ ] `AudioManager.setMode(MODE_IN_COMMUNICATION)` on call start,
      restore on end.
- [ ] Speaker / earpiece via `setCommunicationDevice()` (API 31+).

**Deliverable:** Toggle works on Tab A9+. Bluetooth deferred to v1.1.

---

### Phase 7 — Onboarding rework (4h, Day 7 afternoon)

- [ ] Strip dialer-role copy and visuals from `OnboardingScreen`.
- [ ] Add a permissions block for `RECORD_AUDIO`,
      `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`,
      `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- [ ] Remove `genericIntentLauncher.launch(rm.createRequestRoleIntent(...))`
      from `OnboardingActivity`.

**Deliverable:** Clean onboarding flow that no longer asks for the
dialer role.

---

### Phase 8 — Logging & observability (3h, Day 8 morning)

- [ ] Route `Factory.loggingService` to `filesDir/sip-logs/` rotating
      file. Tag with Timber for live tail in dev.
- [ ] CDR enrichment: on `Call.State.End`, capture
      `currentParams.usedAudioPayloadType` (the codec the SBC chose),
      append to the existing `InteractionEntity`-row metadata if a
      free field exists; otherwise log only (don't alter the schema in
      this migration).

**Deliverable:** Operations can pull a SIP log file from a tablet via
USB to debug user-reported issues.

---

### Phase 9 — QA matrix on Tab A9+ (16h, Day 8 afternoon → Day 10 morning)

Manual test pass on production hardware. Same matrix as before, with
two additions specific to Option A.

| Scenario                                     | Expected                            | Validates                  |
|----------------------------------------------|-------------------------------------|----------------------------|
| Call on stable WiFi                          | Bidirectional audio                 | M3                         |
| Call on 4G (LTE)                             | Bidirectional audio                 | **M4** (R2 risk killer)    |
| Call to IVR + DTMF nav                       | IVR responds                        | **M5**                     |
| Call with app minimized                      | Audio continues                     | **M6** + R1                |
| Call with screen off                         | Audio continues, proximity OK       | M6                         |
| Call >60 min                                 | No memory leak / no audio dropout   | Stability                  |
| WiFi → 4G mid-call                           | Call drops cleanly                  | (re-INVITE out of scope)   |
| Hangup remote BYE                            | UI returns to idle                  | State machine              |
| Network loss mid-call                        | UI shows "lost", recovers if back   | Resilience                 |
| Samsung DeviceCare battery-opt ON            | Service survives, audio survives    | **R1**                     |
| Long-call (~1h) on 4G                        | No crash, no native SIGSEGV         | R3                         |
| **Onboarding without dialer-role prompt**    | App reaches MainActivity            | **R7** (Option A specific) |
| **OEM dialer (Samsung Phone) handles SIM calls again** | OEM dialer rings on incoming PSTN | **R8** (Option A specific) |

- [ ] Run all 13 scenarios. Log pass/fail.
- [ ] Bugfix buffer: 4h within this 16h block.

**Deliverable:** Filled matrix in PR description + signed-off log.

---

### Phase 10 — Distribution & doc closure (2h, Day 10 afternoon)

- [ ] Signed APK build with corporate keystore (existing pipeline).
- [ ] Update [`DIALER_SETUP_GUIDE.md`](./DIALER_SETUP_GUIDE.md) — strip
      dialer-role steps, add SIP-permissions steps.
- [ ] Update [`OVERVIEW.md`](./OVERVIEW.md) and
      [`README.md`](./README.md) to reflect that the app is no longer
      a default dialer.
- [ ] Mark [`TELECOM_ARCHITECTURE.md`](./TELECOM_ARCHITECTURE.md)
      banner check — already done in this migration's doc-only phase.
- [ ] Announce rollout plan to operations.

**Deliverable:** APK ready, docs aligned.

---

## 7. Validation milestones

The milestones (M1–M6) are the **empirical replacement** for waiting
on Voselia's answers.

| ID | When           | Setup                                 | Pass criterion                              | Failure → action                                                                 |
|----|----------------|---------------------------------------|---------------------------------------------|----------------------------------------------------------------------------------|
| M1 | End of Day 1   | UDP, no SRTP, no STUN                 | REGISTER receives `200 OK`                  | `401` → wrong auth user / `403` → SBC requires SRTP, jump to M2 / Timeout → port |
| M2 | Mid Day 2      | UDP + SRTP/SDES                       | REGISTER receives `200 OK`                  | `488` → cipher suite mismatch. Try `AES_CM_128_HMAC_SHA1_32` instead of `_80`     |
| M3 | End of Day 4   | INVITE on WiFi                        | Bidirectional audio + capture SDP `200 OK`  | No audio → enable STUN public / `488` → reorder codecs (G.711 first)             |
| M4 | Day 9 (matrix) | INVITE on 4G                          | Bidirectional audio                         | One-way → enable STUN / Both mute → TURN required, escalate to Voselia           |
| M5 | Day 9 (matrix) | DTMF to IVR                           | IVR navigates correctly                     | No response → switch to SIP INFO, then in-band                                   |
| M6 | Day 6 + Day 9  | App minimized during call             | Audio continues > 30s                       | Audio cuts → check `foregroundServiceType=microphone` + battery-opt grant         |

**Capture artifact for every milestone:** the relevant excerpt from
the Linphone log file (REGISTER, INVITE, 200 OK SDP). Paste into
[`SIP_VOSELIA_CONFIG.md` §6.3](./SIP_VOSELIA_CONFIG.md) so the config
doc converges to a fully-verified state alongside the code.

---

## 8. Schedule overview

```
Week 1 (Mon–Fri)
  Day 1  — Phase 0 + 1 ............... Setup + M1 (REGISTER plain)
  Day 2  — Phase 2 + 3.1–3.2 ......... Telecom demolition + Core + M2
  Day 3  — Phase 3.3 ................. Registration state machine
  Day 4  — Phase 3.4 ................. CallSession + M3 (call WiFi)
  Day 5  — Phase 4 ................... CallController refactor

Week 2 (Mon–Fri)
  Day 6  — Phase 5 ................... Foreground Service (M6)
  Day 7  — Phase 6 + 7 ............... Audio routing + Onboarding
  Day 8  — Phase 8 + 9 (start) ....... Logging + QA matrix start
  Day 9  — Phase 9 ................... QA matrix complete (M4, M5, M6)
  Day 10 — Phase 9 (bugfix) + 10 ..... Bugfix + distribution + doc closure
```

Buffer is built into Phase 9 (4h bugfix sub-block). If Phase 9 reveals
a structural issue (TURN required, R8 surfaces), Day 11–12 is honest,
not hidden.

---

## 9. Live risks

Carried over from the estimate, plus 2 new risks specific to Option A.

| ID | Risk                                                                       | Trigger             | Mitigation                                                            |
|----|----------------------------------------------------------------------------|---------------------|-----------------------------------------------------------------------|
| R1 | Samsung One UI / DeviceCare kills Foreground Service                       | Day 6 onward        | Force `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt during onboarding |
| R2 | NAT simétrico (CGN móvil / WiFi corporativo) → audio mudo                  | M4                  | Enable STUN. If still mute → escalate to Voselia for TURN              |
| R3 | Linphone JNI native crash without Java stacktrace                          | Long-call testing   | Native logs to file (Phase 8) for post-mortem                         |
| R5 | Android 15 silenciosamente bloquea mic en background                       | M6                  | Verify `foregroundServiceType=microphone` declaration + runtime perm  |
| **R7** | **App no longer default dialer — agents may try to use SIM-side dialer** | Onboarding          | Communicate to operations + visibly disable any "call via SIM" UI affordance |
| **R8** | **OEM dialer resurfaces for incoming PSTN calls — agents may pick up wrong line** | Production cutover  | Operations training. Make outbound SIP the only documented path. |

---

## 10. Out of scope for v1

Locked out — bring up only after v1 is stable in production:

- **Inbound SIP calls.** No `Core.incomingInvitationEnabled = true`,
  no incoming notification, no Option B accept/reject UI for SIP. The
  REGISTER is for outbound credential proof only.
- **PSTN incoming via the SIM** is left to the OEM dialer (Samsung
  Phone) once we relinquish `ROLE_DIALER`. The app does not intercept
  SIM rings.
- **Bluetooth SCO** routing.
- **Re-INVITE on network change** (WiFi ↔ 4G mid-call). Calls drop on
  network change in v1; UX is honest about it.
- **Call quality indicators** (jitter / RTT / MOS in the UI).
- **Multiple concurrent registrations / SIP accounts.**
- **Video calls.** Disabled at `Core` level.
- **TLS for signaling.** UDP is fine in v1; revisit if security audit
  requires it.
- **Backend-issued credentials (Phase B).** Tracked separately as a
  follow-up epic.

---

## 11. Definition of Done

A PR closing this plan is mergeable when **all** of the following hold:

- [ ] All 10 phases delivered.
- [ ] All 6 milestones (M1–M6) passed and evidenced in
      [`SIP_VOSELIA_CONFIG.md` §6.3](./SIP_VOSELIA_CONFIG.md).
- [ ] QA matrix (§6 Phase 9) filled — 13/13 rows pass.
- [ ] No file in the diff exceeds 1000 lines (project hard cap).
      `PreCallScreen.kt` is already at 1197 — pre-existing issue, do
      not grow it further; surface a follow-up task to split it.
- [ ] CDRs from SIP calls land in the same `InteractionEntity` table
      as before. No schema change.
- [ ] Manual run-through on Tab A9+ documented in the PR body.
- [ ] [`DIALER_SETUP_GUIDE.md`](./DIALER_SETUP_GUIDE.md) updated.
- [ ] [`TELECOM_ARCHITECTURE.md`](./TELECOM_ARCHITECTURE.md) banner
      preserved (already done in doc-only phase).
- [ ] No leftover references to `CallsConnectionService`,
      `CallsInCallService`, `DisconnectCauseMapper`, or
      `RoleManager.ROLE_DIALER` in the source tree.

---

## 12. Change log

| Date       | Change                                                                                   | Author |
|------------|------------------------------------------------------------------------------------------|--------|
| 2026-05-03 | Initial plan (Option Z — Linphone replacement, generic).                                 | —      |
| 2026-05-03 | Pivot to Option A (total replacement of Telecom). Real inventory measured from codebase. Estimate revised 75h → 83h. Added R7, R8. Credentials strategy split into Phase A / Phase B. Branch confirmed `main`. Server `cpbxa.vozelia.com.pa`. | — |
