# calls-agends — Agent Context

**Quick-read entry point for any human or AI agent joining this codebase.**
Read this file first. The rest of `docs/` expands on each topic.

---

## What this project is (in one paragraph)

`calls-agends` is the **Android tablet app** used by field agents of the
**Panama Calls CRM**. Agents open the app, see a queue of clients assigned to
them, call them one by one using the tablet's dialer, write notes during the
call, pick an outcome when the call ends, and schedule follow-ups for
interested clients. The app is **offline-first** (Room DB + WorkManager) and
syncs with a NestJS backend (`calls-core`) deployed at
`https://crediiz-core-production.up.railway.app`.

---

## Target hardware (critical constraint)

**Samsung Galaxy Tab A9+** (11" tablet, Android 13+). This is **NOT a phone app**.
All layouts, touch targets, navigation, and orientation defaults must be
optimized for tablet — primarily **landscape**. See [`TARGET_DEVICE.md`](./TARGET_DEVICE.md)
for specs and UI implications.

> ⚠️ **Open question**: is the target the Tab A9+ **5G** (`SM-X216`, can place
> cellular calls) or the **WiFi-only** variant (`SM-X210`, CANNOT place cellular
> calls)? The current architecture assumes native cellular dialing. If the fleet
> is WiFi-only, VoIP (Twilio/SIP) must replace the native dialer path. Confirm
> with the product owner before building the call flow.

---

## Related projects (the big picture)

| Repo | Role | Location |
|---|---|---|
| **calls-core** | NestJS backend API + MongoDB (Atlas). Source of truth. | `/Users/yornel/projects/vortex/calls-core` |
| **calls-agends** | This repo. Android tablet app for agents. | `/Users/yornel/projects/vortex/calls-agends` |
| Admin panel (web) | Angular + Tailwind. Not in this repo. Consumes the same backend. | N/A — separate repo |

Full product spec lives in `calls-core/docs/` (`MVP_OVERVIEW.md`,
`BACKEND_ARCHITECTURE.md`, `DATA_FLOW.md`, `MOBILE_APP.md`). This `docs/`
folder is specific to the Android app.

---

## Tech stack (TL;DR)

- **Kotlin + Jetpack Compose** (UI)
- **Hilt** (DI) + **KSP** for codegen (kapt is incompatible with current AGP)
- **Room** (local DB, 4 entities: Client, Interaction, Note, FollowUp)
- **Retrofit + Moshi + OkHttp logging** (HTTP)
- **WorkManager** (periodic background sync)
- **DataStore Preferences** (JWT token storage)
- **Navigation Compose** (screen routing)
- Java 11, compileSdk 37, minSdk 30

Details: [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## Current status (as of 2026-04-21)

Implemented end-to-end: login, main navigation (Clients / Agenda / Settings
tabs), local DB, sync infrastructure.

In progress / missing: the **entire call flow** (Pre-Call, In-Call, Post-Call
screens are navigation stubs), Telecom integration, follow-up local
notifications, auto-call mode.

Full breakdown: [`IMPLEMENTATION_STATUS.md`](./IMPLEMENTATION_STATUS.md).

---

## Where to start if you're…

| You are… | Read in this order |
|---|---|
| A new dev joining the team | `OVERVIEW.md` → `ARCHITECTURE.md` → `IMPLEMENTATION_STATUS.md` → `DEVELOPMENT_PLAN.md` |
| A Claude agent picking up a task | This file → `DEVELOPMENT_PLAN.md` (grab next unchecked task) → `IMPLEMENTATION_STATUS.md` → relevant code under `app/src/main/java/com/project/vortex/callsagent/` |
| A QA / designer | `OVERVIEW.md` → `TARGET_DEVICE.md` |
| Evaluating if to move to VoIP | `TARGET_DEVICE.md` (§ "Cellular vs WiFi variant") + `DEVELOPMENT_PLAN.md` GATE-A |

---

## Conventions enforced in this repo

- All code, identifiers, and inline comments **in English**.
- Project documentation (`docs/*.md`) also in English for consistency with
  `calls-core/docs/`.
- SOLID + SoC: every ViewModel / Repository / Screen has a single
  responsibility. No god objects.
- File size cap: **1000 lines** per source file. Split by responsibility
  before exceeding.
- No hardcoded URLs: API base URL lives in `BuildConfig.API_BASE_URL`
  (defined per buildType in [`app/build.gradle.kts`](../app/build.gradle.kts)).

---

## Useful commands

```bash
# Rebuild debug APK (hits Railway by default)
./gradlew clean assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew test

# Run lint
./gradlew lint
```

---

## Doc index

- [`OVERVIEW.md`](./OVERVIEW.md) — product vision, agent workflow, call funnel
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — tech stack, module map, data flow
- [`TARGET_DEVICE.md`](./TARGET_DEVICE.md) — Tab A9+ specs, tablet UI rules
- [`IMPLEMENTATION_STATUS.md`](./IMPLEMENTATION_STATUS.md) — done / partial / missing (state snapshot)
- [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) — step-by-step roadmap with DoD per task
