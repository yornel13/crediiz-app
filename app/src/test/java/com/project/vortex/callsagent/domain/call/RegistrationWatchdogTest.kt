package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.data.sip.SipConfig
import com.project.vortex.callsagent.data.sip.SipRegistrationState
import com.project.vortex.callsagent.data.voip.VoipAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RegistrationWatchdog] — the reactive recovery of a dropped
 * SIP registration. Covers the pure decision and the time-driven grace/backoff
 * loop using virtual time so the suite stays instant and deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationWatchdogTest {

    private val available: VoipAvailability =
        VoipAvailability.Available(SipConfig(server = "sbc", user = "u", password = "p"))

    private val grace = 4_000L
    private val backoff = listOf(2_000L, 5_000L)

    // ─── Pure decision: needsRecovery ────────────────────────────────────────

    @Test
    fun `needsRecovery true when available, idle and not registered`() {
        assertTrue(RegistrationWatchdog.needsRecovery(SipRegistrationState.Cleared, false, available))
        assertTrue(RegistrationWatchdog.needsRecovery(SipRegistrationState.Idle, false, available))
        assertTrue(RegistrationWatchdog.needsRecovery(SipRegistrationState.InProgress, false, available))
        assertTrue(RegistrationWatchdog.needsRecovery(SipRegistrationState.Failed("x"), false, available))
    }

    @Test
    fun `needsRecovery false when registered`() {
        assertFalse(RegistrationWatchdog.needsRecovery(SipRegistrationState.Registered, false, available))
    }

    @Test
    fun `needsRecovery false during a call`() {
        assertFalse(RegistrationWatchdog.needsRecovery(SipRegistrationState.Cleared, true, available))
    }

    @Test
    fun `needsRecovery false without a VoIP line`() {
        assertFalse(RegistrationWatchdog.needsRecovery(SipRegistrationState.Cleared, false, VoipAvailability.Unassigned))
        assertFalse(RegistrationWatchdog.needsRecovery(SipRegistrationState.Cleared, false, VoipAvailability.Unknown))
    }

    // ─── Reactive behaviour ──────────────────────────────────────────────────

    @Test
    fun `recovers after grace then escalates on backoff`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Registered)
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        // Healthy → nothing happens.
        assertEquals(0, recoverCount)

        // Registration drops.
        reg.value = SipRegistrationState.Cleared
        runCurrent()
        assertEquals("must wait out the grace window", 0, recoverCount)

        advanceTimeBy(grace); runCurrent()
        assertEquals("first recovery after grace", 1, recoverCount)

        advanceTimeBy(backoff[0]); runCurrent()
        assertEquals("second after backoff[0]", 2, recoverCount)

        advanceTimeBy(backoff[1]); runCurrent()
        assertEquals("third after backoff[1]", 3, recoverCount)

        advanceTimeBy(backoff[1]); runCurrent()
        assertEquals("backoff caps at the last value", 4, recoverCount)
    }

    @Test
    fun `transient InProgress that recovers before grace never re-registers`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Registered)
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        // Normal register handshake: brief InProgress, then Ok before grace.
        reg.value = SipRegistrationState.InProgress
        runCurrent()
        advanceTimeBy(grace / 2); runCurrent()
        reg.value = SipRegistrationState.Registered
        runCurrent()
        advanceTimeBy(grace * 3); runCurrent()

        assertEquals("no spurious recovery for a normal handshake", 0, recoverCount)
    }

    @Test
    fun `never re-registers while a call is active`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Cleared)
        val inCall = MutableStateFlow(true)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        advanceTimeBy(grace * 5); runCurrent()
        assertEquals("must not disturb media mid-call", 0, recoverCount)
    }

    @Test
    fun `recovers once the call ends with a dropped registration`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Cleared)
        val inCall = MutableStateFlow(true)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        advanceTimeBy(grace * 2); runCurrent()
        assertEquals(0, recoverCount)

        // Hang up.
        inCall.value = false
        runCurrent()
        advanceTimeBy(grace); runCurrent()
        assertEquals("recovers after the call ends", 1, recoverCount)
    }

    @Test
    fun `stops retrying once the registration is restored`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Failed("down"))
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        advanceTimeBy(grace); runCurrent()
        assertEquals(1, recoverCount)

        // Registration comes back.
        reg.value = SipRegistrationState.Registered
        runCurrent()
        advanceTimeBy(backoff[1] * 5); runCurrent()
        assertEquals("no further attempts after recovery", 1, recoverCount)
    }

    @Test
    fun `does not re-register when the agent has no VoIP account`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Cleared)
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow<VoipAvailability>(VoipAvailability.Unassigned)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        advanceTimeBy(grace * 5); runCurrent()
        assertEquals(0, recoverCount)
    }

    @Test
    fun `oscillating among non-registered states does not reset the backoff`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Registered)
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()

        reg.value = SipRegistrationState.Cleared
        runCurrent()
        advanceTimeBy(grace); runCurrent()
        assertEquals(1, recoverCount)

        // needsRecovery stays true across these, so distinctUntilChanged does
        // NOT re-emit and the grace/backoff loop keeps going uninterrupted.
        reg.value = SipRegistrationState.InProgress; runCurrent()
        reg.value = SipRegistrationState.Cleared; runCurrent()
        reg.value = SipRegistrationState.Failed("x"); runCurrent()

        advanceTimeBy(backoff[0]); runCurrent()
        assertEquals("backoff continues despite oscillation", 2, recoverCount)
    }

    @Test
    fun `keeps retrying when recover throws`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Cleared)
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) {
            recoverCount++
            throw RuntimeException("SIP boom")
        }.start(backgroundScope)
        runCurrent()

        advanceTimeBy(grace); runCurrent()
        assertEquals("first attempt threw but was swallowed", 1, recoverCount)
        advanceTimeBy(backoff[0]); runCurrent()
        assertEquals("watchdog survives a throwing recover and retries", 2, recoverCount)
        advanceTimeBy(backoff[1]); runCurrent()
        assertEquals(3, recoverCount)
    }

    @Test
    fun `does not attempt before the exact grace and backoff deadlines`() = runTest {
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Registered)
        val inCall = MutableStateFlow(false)
        val avail = MutableStateFlow(available)
        var recoverCount = 0
        watchdog(reg, inCall, avail) { recoverCount++ }.start(backgroundScope)
        runCurrent()
        reg.value = SipRegistrationState.Cleared; runCurrent()

        advanceTimeBy(grace - 1); runCurrent()
        assertEquals("nothing fires 1ms before grace", 0, recoverCount)
        advanceTimeBy(1); runCurrent()
        assertEquals("fires exactly at grace", 1, recoverCount)

        advanceTimeBy(backoff[0] - 1); runCurrent()
        assertEquals("no second attempt 1ms before backoff[0]", 1, recoverCount)
        advanceTimeBy(1); runCurrent()
        assertEquals("second attempt exactly at backoff[0]", 2, recoverCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty backoff list`() {
        RegistrationWatchdog(
            registrationState = MutableStateFlow(SipRegistrationState.Registered),
            inCall = MutableStateFlow(false),
            availability = MutableStateFlow(available),
            recover = {},
            graceMillis = grace,
            backoffMillis = emptyList(),
        )
    }

    private fun watchdog(
        reg: MutableStateFlow<SipRegistrationState>,
        inCall: MutableStateFlow<Boolean>,
        avail: MutableStateFlow<VoipAvailability>,
        recover: suspend () -> Unit,
    ) = RegistrationWatchdog(
        registrationState = reg,
        inCall = inCall,
        availability = avail,
        recover = recover,
        graceMillis = grace,
        backoffMillis = backoff,
    )
}
