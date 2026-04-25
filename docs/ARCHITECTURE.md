# ARCHITECTURE

## 1. Layered architecture

Clean architecture variant with three concentric layers:

```
┌─────────────────────────────────────────────────────┐
│  presentation/        (Compose UI + ViewModels)     │
│      │                                              │
│      ▼                                              │
│  domain/             (models, repository interfaces)│
│      │                                              │
│      ▼                                              │
│  data/               (Room, Retrofit, DataStore,    │
│                       sync, repository impls)       │
└─────────────────────────────────────────────────────┘
```

- **presentation** depends on **domain** only.
- **domain** is pure Kotlin — no Android, no Retrofit, no Room.
- **data** implements domain interfaces, is the only layer that touches
  persistence or network.
- DI wires data implementations into domain interfaces via Hilt
  (`di/RepositoryModule.kt`).

Rule: **never import `data/*` from `presentation/*`**. Go through the
domain repository abstraction.

---

## 2. Package map

Root package: `com.project.vortex.callsagent`

```
callsagent/
├── CallsAgentApp.kt              ← @HiltAndroidApp + WorkManager init
├── MainActivity.kt               ← @AndroidEntryPoint + NavHost entry
│
├── common/
│   ├── enums/                    ← CallOutcome, ClientStatus, etc.
│   └── util/                     ← JwtDecoder, date helpers
│
├── data/
│   ├── local/
│   │   ├── db/                   ← Room DB + DAOs
│   │   ├── entity/               ← @Entity classes (Client, Interaction,
│   │   │                          Note, FollowUp)
│   │   └── preferences/          ← DataStore wrappers (JWT, settings)
│   ├── mapper/                   ← entity ↔ domain model mappers
│   ├── remote/
│   │   ├── api/                  ← Retrofit interfaces (AuthApi, SyncApi,
│   │   │                          ClientsApi*, FollowUpsApi*)
│   │   ├── dto/                  ← request/response DTOs
│   │   └── interceptor/          ← auth header injector
│   ├── repository/               ← Repository implementations
│   └── sync/                     ← SyncManager, SyncWorker,
│                                   SyncScheduler, ConnectivityObserver
│
├── di/
│   ├── AppModule.kt              ← general bindings
│   ├── DatabaseModule.kt         ← Room provider
│   ├── NetworkModule.kt          ← Retrofit/OkHttp provider
│   └── RepositoryModule.kt       ← @Binds domain→data
│
├── domain/
│   ├── model/                    ← pure Kotlin data classes
│   └── repository/               ← repository interfaces
│
├── presentation/
│   ├── navigation/               ← AppNavGraph, routes
│   ├── login/                    ← LoginScreen + ViewModel
│   ├── home/                     ← HomeScreen (bottom nav host)
│   ├── clients/                  ← ClientsScreen + ViewModel
│   ├── agenda/                   ← AgendaScreen + ViewModel
│   └── settings/                 ← SettingsScreen + ViewModel
│
└── ui/theme/                     ← Material 3 theme
```

\* `ClientsApi` and `FollowUpsApi` files exist but are not yet fully consumed.

---

## 3. Dependencies (TL;DR)

Defined in [`app/build.gradle.kts`](../app/build.gradle.kts) via the
version catalog [`gradle/libs.versions.toml`](../gradle/libs.versions.toml).

| Concern | Library |
|---|---|
| UI | Jetpack Compose (BOM) + Material 3 |
| Navigation | `androidx.navigation:navigation-compose` |
| DI | Hilt (android + navigation-compose + work) with KSP codegen |
| Local DB | Room (runtime + ktx + KSP compiler) |
| HTTP | Retrofit + Moshi + OkHttp logging |
| Background work | WorkManager + Hilt-work |
| Preferences | DataStore Preferences |
| Async | Kotlinx Coroutines |

Notes:
- **KSP is used instead of kapt** because AGP 9.1.1 ships Kotlin built-in and
  kapt is incompatible. Do NOT add kapt to this project.
- Moshi codegen is also via KSP
  (`com.squareup.moshi:moshi-kotlin-codegen:1.15.1`).

---

## 4. Data flow — read path (UI reads a list)

```
ClientsScreen ──► ClientsViewModel ──► ClientRepository (domain interface)
                                           │
                                           ▼ impl in data/repository/
                                       ClientRepositoryImpl
                                           │
                                           ▼
                                       ClientDao (Room)  ──► StateFlow<List<Client>>
```

The ViewModel exposes a `StateFlow` backed by Room's reactive queries.
The network never appears in a read — only in background sync.

---

## 5. Data flow — write path (agent saves a post-call)

```
PostCallScreen ──► PostCallViewModel ──► InteractionRepository.create(...)
                                              │
                                              ▼
                                          Room: insert interaction + note
                                          (+ follow-up if interested)
                                              │
                                              ▼
                                          SyncManager.requestImmediateSync()
                                              │
                                              ▼
                                          POST /api/sync/interactions
                                          (batch of pending items)
                                              │
                                              ▼
                                          Room: mark syncStatus = SYNCED
                                              │
                                              ▼
                                          Refresh clients & follow-ups
                                          (re-fetch from /api/clients/assigned)
```

Every write lands in Room **before** any network call. Loss of connectivity
never blocks the UI.

---

## 6. Sync internals

Three moving parts:

1. **`SyncManager`** — imperative entry point. Holds a Mutex to serialize
   sync runs. Queries Room for items with `syncStatus IN (PENDING, FAILED)`,
   batches them into one `POST /api/sync/interactions`, and updates each
   item's `syncStatus` based on the response.
2. **`SyncWorker`** — a `CoroutineWorker` that WorkManager runs every ~15–30
   minutes as a `PeriodicWorkRequest`. Delegates to `SyncManager`.
3. **`ConnectivityObserver`** — listens to `ConnectivityManager.NetworkCallback`
   and enqueues a one-shot sync when the network comes back.

Dedup on the backend uses `mobileSyncId` UUIDs generated on-device at
creation time. That means retries are **always safe**.

---

## 7. Authentication

- Login: `POST /api/auth/login` returns `{ accessToken }`.
- Token stored in **encrypted DataStore** (`TokenStore`).
- `AuthInterceptor` (in `data/remote/interceptor/`) injects
  `Authorization: Bearer <token>` on every outgoing request.
- **Token expiration** is checked locally via `JwtDecoder` before each call;
  expired tokens trigger logout + nav to LoginScreen.

No refresh-token flow yet (backend issues 24h tokens). When the token
expires, the agent re-enters credentials.

---

## 8. Navigation

`AppNavGraph.kt` defines top-level routes:

| Route | Screen | Status |
|---|---|---|
| `login` | `LoginScreen` | done |
| `home` | `HomeScreen` (holds bottom-nav: Clients/Agenda/Settings) | done |
| `precall/{clientId}` | `PreCallScreen` | **stub** |
| `incall/{clientId}` | `InCallScreen` | **stub** |
| `postcall/{clientId}/{interactionId}` | `PostCallScreen` | **stub** |

The stubs are plain TODO Composables that render placeholder text.

---

## 9. Build configuration

```kotlin
// app/build.gradle.kts
defaultConfig {
    applicationId = "com.project.vortex.callsagent"
    minSdk = 30          // Android 11+ (Tab A9+ ships with 13)
    targetSdk = 37
    buildConfigField("String", "API_BASE_URL",
        "\"https://crediiz-core-production.up.railway.app/api/\"")
}
```

- Single flavor, two buildTypes (`debug`, `release`). Both currently point
  at Railway; commented-out line in `debug` switches to
  `http://10.0.2.2:3000/api/` for local dev with the NestJS backend on
  localhost:3000.
- No ProGuard / R8 shrinking enabled yet (`isMinifyEnabled = false` on
  release). Consider enabling before shipping.

---

## 10. Architectural rules to enforce on PRs

1. **No Retrofit or Room imports under `presentation/`.**
2. **No hardcoded URLs** — always `BuildConfig.API_BASE_URL`.
3. **Every mobile-created record has a `mobileSyncId: UUID`.** If you add a
   new synced entity, follow the pattern.
4. **ViewModels expose `StateFlow`, not `LiveData`.** Consistency with the
   existing codebase.
5. **No `runBlocking`** in production code. Use `viewModelScope`,
   `lifecycleScope`, or WorkManager.
6. **Source file cap: 1000 lines.** Split by responsibility before you hit
   it.
7. **Strings** (user-facing text) belong in `res/values/strings.xml`, not
   hardcoded in Composables — pending cleanup for MVP.
