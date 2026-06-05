package com.project.vortex.callsagent.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Regression suite for the timezone policy
 * (`calls-core/docs/TIMEZONE_POLICY.md`).
 *
 * **Bug class we are preventing:**
 * A Venezuelan agent (UTC-4) picks "tomorrow at 2 pm" in a date/time
 * picker. The product intent is "2 pm Panama" (the client's
 * wall-clock). If we zonify the picked `LocalDateTime` with
 * `ZoneId.systemDefault()` (= Caracas), the resulting `Instant`
 * is 18:00 UTC — which renders as 1 pm Panama. The follow-up lands
 * one hour early, every single day. With `BusinessConfig.BUSINESS_TIMEZONE`
 * the same input becomes 19:00 UTC = 2 pm Panama. Correct.
 *
 * Each test runs under a JVM TZ set to `America/Caracas` (the agent's
 * device) so the assertions exercise the exact production scenario.
 * The `@Before` / `@After` pair restores the global TZ between tests
 * to avoid leaking into unrelated suites.
 */
class BusinessConfigTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun forceCaracasJvmTimezone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Caracas"))
    }

    @After
    fun restoreOriginalTimezone() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun `BUSINESS_TIMEZONE is America Panama`() {
        assertEquals(ZoneId.of("America/Panama"), BusinessConfig.BUSINESS_TIMEZONE)
    }

    @Test
    fun `picker LocalDateTime zonified with BUSINESS_TIMEZONE produces Panama Instant even when device is Caracas`() {
        // Agent picks "23 May 2026, 2 pm" on the picker UI.
        val pickedDate = LocalDate.of(2026, 5, 23)
        val pickedTime = LocalTime.of(14, 0)
        val pickerInput: LocalDateTime = pickedDate.atTime(pickedTime)

        // Production code path: zonify with the business clock.
        val correctInstant: Instant = pickerInput
            .atZone(BusinessConfig.BUSINESS_TIMEZONE)
            .toInstant()

        // The bug: zonify with the device TZ (Caracas, UTC-4).
        val buggyInstant: Instant = pickerInput
            .atZone(ZoneId.systemDefault())
            .toInstant()

        // 14:00 Panama (UTC-5) = 19:00 UTC.
        assertEquals(Instant.parse("2026-05-23T19:00:00Z"), correctInstant)
        // 14:00 Caracas (UTC-4) = 18:00 UTC — exactly 1 h earlier in Panama.
        assertEquals(Instant.parse("2026-05-23T18:00:00Z"), buggyInstant)

        // The bug would land the follow-up 1 h before the agent meant.
        assertEquals(3600L, correctInstant.epochSecond - buggyInstant.epochSecond)
    }

    @Test
    fun `LocalDate now with BUSINESS_TIMEZONE returns Panama calendar day not device calendar day`() {
        // A moment that lives on different calendar days depending on TZ:
        // 24 May 2026, 02:30 UTC = 23 May 21:30 Panama BUT 23 May 22:30 Caracas
        // (same day in both, less dramatic). Use a stronger boundary:
        // 24 May 2026, 04:30 UTC = 23 May 23:30 Panama, 24 May 00:30 Caracas.
        val boundary = Instant.parse("2026-05-24T04:30:00Z")

        val panamaDay = boundary.atZone(BusinessConfig.BUSINESS_TIMEZONE).toLocalDate()
        val caracasDay = boundary.atZone(ZoneId.systemDefault()).toLocalDate()

        assertEquals(LocalDate.of(2026, 5, 23), panamaDay)
        assertEquals(LocalDate.of(2026, 5, 24), caracasDay)
        // ↑ This is exactly the "Today bucket drift" bug the agenda
        // would suffer if grouping used systemDefault().
    }

    @Test
    fun `Instant read-back with BUSINESS_TIMEZONE renders correct wall clock from any device`() {
        // Backend sends 19:00 UTC (= 2 pm Panama). Agent in Caracas
        // opens the app — must see "2 pm", not "3 pm".
        val wireFormat = Instant.parse("2026-05-23T19:00:00Z")

        val displayed = wireFormat
            .atZone(BusinessConfig.BUSINESS_TIMEZONE)
            .toLocalTime()

        assertEquals(LocalTime.of(14, 0), displayed)
    }
}
