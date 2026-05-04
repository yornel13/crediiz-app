# SIP_ENGINE_BOUNDARIES — Portability contract for the `data/sip/` package

**Status:** locked architectural reference. Read before adding any
file under `app/src/main/java/.../data/sip/` or modifying its public
surface.

**Last updated:** 2026-05-03.

---

## 1. Goal

The SIP/VoIP engine must stay **decoupled enough that we could lift
the package and drop it into another Android app of the group with
zero or near-zero changes**. Today this is implemented at **Level 1**:
a self-contained package with strict import discipline. If portability
ever needs to be hardened (e.g., publishing as an AAR), the
straightforward upgrade is **Level 2** (separate Gradle module);
this doc explains how to get there without rewriting.

The decision to start at Level 1 was made on 2026-05-03 — Level 2 was
considered and deferred because the additional ~2h of Gradle setup
and module boundary work was judged not worth it before the engine
has shipped to production once.

---

## 2. The boundary in one sentence

**Code inside `data/sip/` knows nothing about this app's domain,
DI framework, build config, or persistence.** It only knows about
the Linphone SDK, Android `Context`, and Kotlin coroutines/flows.

---

## 3. Allowed imports inside `data/sip/`

| Allowed                                                                        | Why                                                              |
|--------------------------------------------------------------------------------|------------------------------------------------------------------|
| `android.content.Context`                                                      | Linphone needs it to bootstrap (`filesDir`, audio, network)      |
| `android.os.Handler`, `HandlerThread`, `util.Log`                              | Threading + logging primitives, no app coupling                  |
| `org.linphone.core.*`                                                          | The actual SDK we wrap                                           |
| `kotlinx.coroutines.*` (StateFlow, MutableStateFlow, suspend, etc.)            | Idiomatic Kotlin async, present in any modern Android project    |
| `java.time.Instant` (and other `java.time.*`)                                  | Standard library                                                 |
| `javax.inject.*` (annotations only — `@Inject`, `@Singleton`)                  | Standard JSR-330 annotations; not Hilt-specific. Optional.       |

## 4. Forbidden imports inside `data/sip/`

If you find any of these in a file under `data/sip/`, the boundary
has been broken — fix it instead of adding a new violation.

| Forbidden                                                          | Why it breaks portability                                          |
|--------------------------------------------------------------------|--------------------------------------------------------------------|
| `dagger.hilt.*`, `dagger.*`                                        | Hilt is this app's choice — another app might use Koin or manual DI |
| `com.project.vortex.callsagent.BuildConfig`                        | Build constants are app-specific. Read them in `:app` and pass via constructor |
| `com.project.vortex.callsagent.domain.*`                           | Domain models (Client, Interaction, Note, etc.) belong to the app  |
| `com.project.vortex.callsagent.data.local.*`                       | Room entities and DAOs are app-specific                             |
| `com.project.vortex.callsagent.data.remote.*`                      | Backend DTOs are app-specific                                      |
| `com.project.vortex.callsagent.data.sync.*`                        | Sync is app-specific                                                |
| `com.project.vortex.callsagent.presentation.*`                     | UI is app-specific                                                  |
| `androidx.hilt.*`                                                  | Hilt extensions, same reasoning                                    |
| `com.project.vortex.callsagent.domain.call.model.*`                | The app's `CallUiState` is for the UI — the engine has its own state model |

The `domain.call.model.CallUiState` rule above means **the engine does
NOT return `CallUiState`**. It returns `SipRegistrationState` and
exposes a per-call `state: StateFlow<CallState>` (an internal sealed
class living in `data/sip/`). The mapping between SIP states and the
UI state happens at the `CallController` layer in `:app` (Phase 4).

---

## 5. Public surface of the package

What `:app` is allowed to import from `data/sip/`:

| Class / interface                          | Stability     |
|--------------------------------------------|---------------|
| `data.sip.SipConfig`                       | Public, stable |
| `data.sip.SipRegistrationState`            | Public, stable |
| `data.sip.LinphoneCoreManager` *(see §6)*  | Public, stable |
| `data.sip.CallSession`                     | Public, stable |
| `data.sip.auth.SipCredentialsProvider`     | Public, stable (interface) |

What lives **inside** the package and `:app` should **not** depend
on directly: any future class added to `data/sip/internal/`,
configuration constants like `RTP_PORT_MIN`, `OPUS_MAX_BITRATE_KBPS`,
or the implementation of audio device routing. Treat them as private
to the engine.

---

## 6. DI strategy — the only Hilt rule

The classes under `data/sip/` may use **JSR-330 annotations**
(`@Inject`, `@Singleton`) because they are part of `javax.inject` and
are vendor-neutral — Hilt, Dagger, Koin (with a tiny adapter), and
manual instantiation all consume them. **They must NOT use Hilt-only
annotations** (`@HiltAndroidApp`, `@HiltViewModel`, `@AndroidEntryPoint`,
`@ApplicationContext`).

Concretely:

- `LinphoneCoreManager` constructor takes `(Context, SipCredentialsProvider)`
  with `@Inject` — **without** `@ApplicationContext`. The Hilt module
  in `di/SipModule.kt` is the only place where Hilt knows that the
  `Context` must be the app context.
- `BuildConfigSipCredentialsProvider` keeps `@Inject` and `@Singleton`
  (JSR-330) but does **not** import any `dagger.hilt.*` symbol.
- `SipModule` (in `di/`, **not** `data/sip/di/`) is the **single
  point** where Hilt and the SIP engine meet. It binds
  `SipCredentialsProvider` and provides the application `Context` for
  `LinphoneCoreManager` construction.

This means: **drop `data/sip/` into another app, add a new Hilt/Koin
module that supplies `Context` + `SipCredentialsProvider`, and the
engine works.** No code edits inside `data/sip/`.

---

## 7. Configuration provisioning — Phase A vs Phase B swap

The `SipCredentialsProvider` interface decouples the engine from
*where* credentials come from. Today (Phase A) the implementation
reads `BuildConfig`. Tomorrow (Phase B) the implementation will hit
the backend. The engine doesn't change either way.

```
+------------------------+        +-------------------------------------+
| LinphoneCoreManager    | ---->  | SipCredentialsProvider (interface)  |
|   (data/sip/)          |        |   data/sip/auth/                    |
+------------------------+        +-------------------------------------+
                                              ^
                                              |  is implemented by
                          +-------------------+-------------------+
                          |                                       |
            +-----------------------------+         +-----------------------------+
            | BuildConfigSipCredentialsProvider |   | BackendSipCredentialsProvider |
            |   (Phase A — :app)                |   |   (Phase B — :app)            |
            +-----------------------------+         +-----------------------------+
            reads BuildConfig.SIP_*                  fetches GET /api/agents/me/sip
```

Both implementations live in `:app`, **not** in `data/sip/`. The
package only exposes the interface.

---

## 8. Outbound call flow (decided)

```
+--------+   placeCall(numberOrUri)   +-----------------------+
| :app   | ---------------------------> | LinphoneCoreManager  |
| caller |                              |   (data/sip/)        |
+--------+ <----- CallSession --------- +-----------------------+
                                                   |
                                                   |  inviteAddress
                                                   v
                                        +----------------------+
                                        |  Linphone Core       |
                                        |  (UDP -> Voselia)    |
                                        +----------------------+
                                                   |
                                                   |  Call.State updates
                                                   v
                                        +----------------------+
                                        |  CallSession         |
                                        |  exposes:            |
                                        |   state: StateFlow   |
                                        |   isMuted: StateFlow |
                                        |   setMuted, dtmf,    |
                                        |   disconnect         |
                                        +----------------------+
```

Key contract decisions, locked:

- `placeCall` is `suspend` and returns `CallSession?`. It returns
  `null` if address parsing or `inviteAddress()` failed.
- The engine **does not own** the lifecycle of `CallSession`.
  The caller may keep it as long as needed. After
  `state == Disconnected`, the session is dead — do not reuse.
- The engine forces `Core.outputAudioDevice = Speaker` before every
  outbound INVITE (Tab A9+ is hands-free). Earpiece / Bluetooth
  routing is **not** offered by the engine in v1; the caller can
  manipulate `Core` directly via a separate route helper if needed
  later.
- Phone numbers are sanitized inside the engine: `+`, digits, nothing
  else. Spaces, dashes, parens are stripped. Full SIP URIs (those
  starting with `sip:`) pass through verbatim.

---

## 9. Registration lifecycle (decided)

```
caller calls register()
         |
         v
+-------------------+        +----------------+
| LinphoneCoreManager| --->  | Core.start()   |
+-------------------+        +----------------+
         |
         v
   addAuthInfo + addAccount + setDefaultAccount
         |
         v
   StateFlow emits InProgress -> Registered (or Failed)
```

- `register()` is **idempotent**. Calling it twice clears any prior
  account and re-registers.
- Re-REGISTER is handled internally by Linphone (refresher), at ~90%
  of `Expires` (3600 s default → refresh at ~54 min). The caller
  doesn't manage timing.
- The `registrationState: StateFlow<SipRegistrationState>` is the
  single source of truth for the rest of the app. Every UI element
  that depends on registration listens to this flow.

---

## 10. Hangup and cleanup

- `CallSession.disconnect()` is safe to call in any state. If the
  call is already `End`/`Released`/`Error`, it no-ops.
- The `CallSession` `state` flow always reaches `Disconnected` exactly
  once before the wrapper goes dormant.
- Linphone-side cleanup (audio streams, RTP sockets, ZRTP context)
  happens automatically when the SDK transitions to `Released`.

---

## 11. Threading

- All Linphone Core calls are made on the **iterate thread** (a
  dedicated `HandlerThread` named `linphone-iterate`). The engine
  posts work via `withCore { core -> ... }` so the SDK never sees
  cross-thread access to the Core.
- The `iterate()` loop runs every 20 ms (Linphone default for active
  state).
- `StateFlow` updates are posted on whichever thread Linphone
  delivers the listener callback on. UI consumers must collect on the
  main dispatcher (handled by Compose).

---

## 12. Path to Level 2 (future, if ever needed)

If the engine needs to be reusable across Gradle projects (publishable
AAR, multi-app), the upgrade to Level 2 is mechanical:

1. Create `sip/build.gradle.kts` (Android Library plugin, namespace
   `com.project.vortex.sip`).
2. Move `data/sip/**` → `sip/src/main/java/com/project/vortex/sip/`
   (and update package declarations).
3. Update `settings.gradle.kts` to `include(":sip")`.
4. Add `implementation(project(":sip"))` to `app/build.gradle.kts`.
5. Move the Linphone dep declaration to `sip/build.gradle.kts`.
6. Keep `BuildConfigSipCredentialsProvider` and `SipModule` in `:app`
   — they are app-specific by design.

The Linphone Maven repo declaration in `settings.gradle.kts` already
works for both setups.

**Estimated effort to upgrade today:** ~1.5h. Lower than the original
Level 2 estimate because the package is already disciplined.

---

## 13. Enforcement (advisory)

These rules are enforced socially, not by tooling, in v1. If the
boundary becomes a recurring problem, add a Detekt rule or a Gradle
verification task that fails on forbidden imports inside `data/sip/`.

---

## 14. Change log

| Date       | Change                                                          |
|------------|-----------------------------------------------------------------|
| 2026-05-03 | Initial — Level 1 contract locked. Path to Level 2 documented. |
