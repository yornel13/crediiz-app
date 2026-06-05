# Timezone Policy — Mobile (calls-agends)

> Thin mirror of the canonical policy in
> [`calls-core/docs/TIMEZONE_POLICY.md`](../../calls-core/docs/TIMEZONE_POLICY.md).
> Read that first. This document only captures the mobile-specific
> enforcement.

---

## TL;DR

- **Single constant**: `com.project.vortex.callsagent.common.BusinessConfig.BUSINESS_TIMEZONE` (`ZoneId.of("America/Panama")`).
- **Never use `ZoneId.systemDefault()`** in `presentation/` or `domain/` code. The agent's device may be in Venezuela; the business clock is Panamá.
- Wire format to backend: `Instant` serialised as ISO with `Z`. The backend's `@IsExplicitOffsetIso()` will reject anything else.

---

## The bug class this prevents

A Venezuelan agent (`America/Caracas`, UTC-4) picks "tomorrow at 2 pm"
in a date/time picker. Product intent: **2 pm Panamá** (when the
client is available). If the picker zonifies the captured
`LocalDateTime` with `ZoneId.systemDefault()`, the result is 2 pm
Caracas = **1 pm Panamá** — every single follow-up lands one hour
early.

`BusinessConfig.BUSINESS_TIMEZONE` removes the ambiguity:

```kotlin
// ✅ Correct
val instant = pickedDate.atTime(pickedTime)
    .atZone(BusinessConfig.BUSINESS_TIMEZONE)
    .toInstant()

// ❌ Wrong — drifts by the agent's UTC offset vs Panamá
val instant = pickedDate.atTime(pickedTime)
    .atZone(ZoneId.systemDefault())
    .toInstant()
```

## Read-back is symmetric

When rendering an `Instant` received from the backend, format against
the business clock too, so the wall-clock matches what the admin and
client see:

```kotlin
// ✅ Correct
val displayed = instant
    .atZone(BusinessConfig.BUSINESS_TIMEZONE)
    .format(formatter)

// ❌ Wrong — agent in Caracas sees one hour later than the admin
val displayed = instant.atZone(ZoneId.systemDefault()).format(formatter)
```

## Calendar-day bucketing

`LocalDate.now()` without an argument uses the device timezone.
`AgendaViewModel`'s "Hoy / Mañana / Esta semana" bucketing MUST use
the business calendar, otherwise a follow-up at "23 May 23:30 Panamá"
appears as "Mañana" to the Caracas agent (= 24 May 00:30 Caracas) and
"Hoy" to the admin.

```kotlin
// ✅ Correct
val today = LocalDate.now(BusinessConfig.BUSINESS_TIMEZONE)

// ❌ Wrong
val today = LocalDate.now()
```

## Tests

`app/src/test/.../common/BusinessConfigTest.kt` forces the JVM TZ to
Caracas via `TimeZone.setDefault(...)` and asserts the produced
`Instant` matches Panamá's wall-clock. Add a similar test whenever
new code introduces a wall-clock decision (picker, calendar
boundary, "today" check).

## Where to grep before merging a PR

```bash
grep -rn "ZoneId.systemDefault\|LocalDate.now()" app/src/main/
```

Hits in `presentation/` or `domain/` are almost certainly bugs.
Hits in `BusinessConfig.kt` itself (in kdoc warning prose) are fine.
