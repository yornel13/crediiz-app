package com.project.vortex.callsagent.data.crash

import com.project.vortex.callsagent.data.sip.SipRegistrationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CrashContextReporter] — verifies the agent id and SIP
 * registration context pushed to the (faked) crash reporter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashContextReporterTest {

    private class FakeCrashReporter : CrashReporter {
        val userIds = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val keys = mutableListOf<Pair<String, String>>()
        override fun setUserId(id: String) { userIds += id }
        override fun log(message: String) { logs += message }
        override fun setKey(key: String, value: String) { keys += key to value }
    }

    @Test
    fun `sets user id on agent id changes, clearing to empty on logout`() = runTest {
        val agentId = MutableStateFlow<String?>(null)
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Idle)
        val fake = FakeCrashReporter()
        CrashContextReporter(fake, agentId, reg).start(backgroundScope)
        runCurrent()
        assertEquals("null agent id maps to empty", listOf(""), fake.userIds)

        agentId.value = "agent-123"; runCurrent()
        assertEquals(listOf("", "agent-123"), fake.userIds)

        agentId.value = null; runCurrent() // logout
        assertEquals(listOf("", "agent-123", ""), fake.userIds)
    }

    @Test
    fun `sets the sip custom key for every state including the initial one`() = runTest {
        val agentId = MutableStateFlow<String?>("a")
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Idle)
        val fake = FakeCrashReporter()
        CrashContextReporter(fake, agentId, reg).start(backgroundScope)
        runCurrent()
        // Initial state is captured as a key right away.
        assertEquals(listOf("sip_registration" to "Idle"), fake.keys)

        reg.value = SipRegistrationState.InProgress; runCurrent()
        reg.value = SipRegistrationState.Registered; runCurrent()
        reg.value = SipRegistrationState.Cleared; runCurrent()
        reg.value = SipRegistrationState.Failed("timeout"); runCurrent()

        assertEquals(
            listOf("Idle", "InProgress", "Registered", "Cleared", "Failed(timeout)"),
            fake.keys.filter { it.first == "sip_registration" }.map { it.second },
        )
    }

    @Test
    fun `logs a breadcrumb only for Registered and Failed, with exact format`() = runTest {
        val agentId = MutableStateFlow<String?>("a")
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Idle)
        val fake = FakeCrashReporter()
        CrashContextReporter(fake, agentId, reg).start(backgroundScope)
        runCurrent()

        reg.value = SipRegistrationState.InProgress; runCurrent()
        reg.value = SipRegistrationState.Registered; runCurrent()
        reg.value = SipRegistrationState.Cleared; runCurrent()
        reg.value = SipRegistrationState.Failed("timeout"); runCurrent()

        // Idle/InProgress/Cleared are transient backoff churn — NOT logged.
        assertEquals(
            listOf("SIP registration → Registered", "SIP registration → Failed(timeout)"),
            fake.logs,
        )
    }

    @Test
    fun `the two collectors are independent`() = runTest {
        val agentId = MutableStateFlow<String?>("a")
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Registered)
        val fake = FakeCrashReporter()
        CrashContextReporter(fake, agentId, reg).start(backgroundScope)
        runCurrent()
        val userIdsAfterStart = fake.userIds.toList()
        val keysAfterStart = fake.keys.toList()

        // An agent-id change must NOT touch the SIP key/log.
        agentId.value = "agent-999"; runCurrent()
        assertEquals(keysAfterStart, fake.keys)

        // A SIP change must NOT touch the user id.
        reg.value = SipRegistrationState.Failed("x"); runCurrent()
        assertEquals(userIdsAfterStart + "agent-999", fake.userIds)
    }

    @Test
    fun `does not duplicate identical consecutive values`() = runTest {
        val agentId = MutableStateFlow<String?>("a")
        val reg = MutableStateFlow<SipRegistrationState>(SipRegistrationState.Registered)
        val fake = FakeCrashReporter()
        CrashContextReporter(fake, agentId, reg).start(backgroundScope)
        runCurrent()

        agentId.value = "a"; runCurrent() // same id
        reg.value = SipRegistrationState.Registered; runCurrent() // same state

        assertEquals("distinctUntilChanged suppresses the duplicate id", listOf("a"), fake.userIds)
        assertEquals("distinctUntilChanged suppresses the duplicate state", 1, fake.keys.size)
        assertFalse("no spurious second breadcrumb", fake.logs.size > 1)
    }
}
