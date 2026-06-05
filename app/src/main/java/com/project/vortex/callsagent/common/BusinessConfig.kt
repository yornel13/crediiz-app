package com.project.vortex.callsagent.common

import java.time.ZoneId

/**
 * Single source of truth for the "business clock" — the timezone we
 * model the world in, regardless of where the device, the agent or
 * the server happen to be.
 *
 * Why this is centralised
 * ───────────────────────
 * The product is multi-country:
 *
 *   - 👤 Clients live in **Panamá** (UTC-5, no DST)
 *   - 📞 Agents work from **Venezuela** (UTC-4)
 *   - 👔 Admins are in **Panamá**
 *   - 🖥️ Server runs on Railway with whatever timezone the host picks
 *
 * When a Venezuelan agent picks "tomorrow at 2 pm" in a date/time
 * picker, the intent is "2 pm Panama" (the client's wall-clock),
 * NOT "2 pm Caracas". If the picker zonifies the `LocalDateTime`
 * with `ZoneId.systemDefault()`, the resulting `Instant` lands one
 * hour early in Panama time — every single follow-up.
 *
 * The rule, mirroring `calls-core/docs/TIMEZONE_POLICY.md §3.3`:
 *
 *   1. Capture user input as `LocalDateTime` (timezone-naive).
 *   2. Zonify with `BusinessConfig.BUSINESS_TIMEZONE` → `ZonedDateTime`.
 *   3. `.toInstant()` for storage / wire.
 *
 * Likewise for read-back: always `Instant.atZone(BUSINESS_TIMEZONE)`
 * before formatting, never `.atZone(ZoneId.systemDefault())`.
 *
 * **Lint your PRs**: `ZoneId.systemDefault()` should not appear in
 * `presentation/` or `domain/` code. If a new screen needs a different
 * zone (e.g. a future "show in my local time" toggle), add a second
 * constant here — never reach for `systemDefault()` ad-hoc.
 */
object BusinessConfig {

    /**
     * The business clock: `America/Panama` (UTC-5, no daylight saving).
     * Frozen at compile time; if Panama ever adopts DST or the product
     * expands to a country with a different baseline, the change goes
     * here and everywhere else flips automatically.
     */
    val BUSINESS_TIMEZONE: ZoneId = ZoneId.of("America/Panama")
}
