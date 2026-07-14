package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OutcomeVisibilityPolicy.contextualAllowedOutcomes] — the
 * cosmetic filter that hides funnel outcomes already passed (or impossible)
 * for the client's current status. The input sets mirror what
 * `CallEndingInsight` emits for Answered vs no-contact endings.
 */
class OutcomeVisibilityPolicyTest {

    // Same set CallEndingInsight emits for an Answered call.
    private val answered = listOf(
        CallOutcome.INTERESTED,
        CallOutcome.SCHEDULED,
        CallOutcome.SOLD,
        CallOutcome.NOT_INTERESTED,
        CallOutcome.DO_NOT_CALL,
        CallOutcome.WRONG_NUMBER,
        CallOutcome.HAS_LOAN,
        CallOutcome.DECEASED,
        CallOutcome.NOT_APPLICABLE,
        CallOutcome.VOICEMAIL,
    )

    private val noContact = listOf(
        CallOutcome.NO_ANSWER,
        CallOutcome.BUSY,
        CallOutcome.OUT_OF_SERVICE,
    )

    private fun visible(allowed: List<CallOutcome>, status: ClientStatus?) =
        OutcomeVisibilityPolicy.contextualAllowedOutcomes(allowed, status)

    @Test
    fun pending_answered_hides_nothing() {
        assertEquals(answered, visible(answered, ClientStatus.PENDING))
    }

    @Test
    fun interested_answered_keeps_interested_as_reconfirmation_hides_not_interested() {
        val result = visible(answered, ClientStatus.INTERESTED)
        // Same-level INTERESTED survives: it re-confirms the rung and
        // refreshes the follow-up cadence ("Continúa interesado").
        assertTrue(CallOutcome.INTERESTED in result)
        assertFalse(CallOutcome.NOT_INTERESTED in result)
        assertTrue(CallOutcome.SCHEDULED in result)
        assertTrue(CallOutcome.SOLD in result)
        // Hard reasons are never touched by the filter.
        assertTrue(CallOutcome.DO_NOT_CALL in result)
        assertTrue(CallOutcome.DECEASED in result)
    }

    @Test
    fun cited_answered_keeps_scheduled_as_reconfirmation_hides_lower_advances() {
        val result = visible(answered, ClientStatus.CITED)
        // Below the rung → hidden; at the rung → visible ("Continúa citado").
        assertFalse(CallOutcome.INTERESTED in result)
        assertTrue(CallOutcome.SCHEDULED in result)
        assertFalse(CallOutcome.NOT_INTERESTED in result)
        assertTrue(CallOutcome.SOLD in result)
    }

    @Test
    fun converted_answered_hides_all_advances_but_keeps_hard_reasons() {
        val result = visible(answered, ClientStatus.CONVERTED)
        assertFalse(CallOutcome.INTERESTED in result)
        assertFalse(CallOutcome.SCHEDULED in result)
        // SOLD is terminal — no follow-up cadence to refresh, so it does NOT
        // survive at its own rung (unlike INTERESTED/SCHEDULED).
        assertFalse(CallOutcome.SOLD in result)
        assertFalse(CallOutcome.NOT_INTERESTED in result)
        // Never empties: hard reasons + voicemail remain.
        assertTrue(result.isNotEmpty())
        assertTrue(CallOutcome.DECEASED in result)
        assertTrue(CallOutcome.VOICEMAIL in result)
    }

    @Test
    fun removed_answered_hides_nothing_to_allow_reactivation() {
        // REMOVED maps to funnel level 0 → advances stay visible to revive
        // the client. Must mirror PENDING exactly (not the enum ordinal).
        assertEquals(answered, visible(answered, ClientStatus.REMOVED))
    }

    @Test
    fun no_contact_call_is_untouched_regardless_of_status() {
        assertEquals(noContact, visible(noContact, ClientStatus.INTERESTED))
        assertEquals(noContact, visible(noContact, ClientStatus.CITED))
    }

    @Test
    fun null_status_fails_open() {
        assertEquals(answered, visible(answered, null))
    }

    @Test
    fun idempotent() {
        val once = visible(answered, ClientStatus.CITED)
        val twice = visible(once, ClientStatus.CITED)
        assertEquals(once, twice)
    }

    @Test
    fun filter_only_subtracts_and_preserves_order() {
        val result = visible(answered, ClientStatus.INTERESTED)
        // Result is a subsequence of the input: order preserved, no additions.
        assertEquals(answered.filter { it in result }, result)
    }

    @Test
    fun advance_target_status_is_the_canonical_funnel_map() {
        assertEquals(ClientStatus.INTERESTED, CallOutcome.INTERESTED.advanceTargetStatus)
        assertEquals(ClientStatus.CITED, CallOutcome.SCHEDULED.advanceTargetStatus)
        assertEquals(ClientStatus.CONVERTED, CallOutcome.SOLD.advanceTargetStatus)
        // Everything else is backend-owned → no local advance.
        assertEquals(null, CallOutcome.NOT_INTERESTED.advanceTargetStatus)
        assertEquals(null, CallOutcome.DO_NOT_CALL.advanceTargetStatus)
        assertEquals(null, CallOutcome.NO_ANSWER.advanceTargetStatus)
    }

    @Test
    fun schedules_follow_up_is_the_canonical_cadence_set() {
        // Exactly INTERESTED and SCHEDULED sustain the follow-up cadence;
        // both the visibility filter and PostCall's mandatory date/time
        // requirement key off this predicate.
        val cadence = CallOutcome.values().filter { it.schedulesFollowUp }
        assertEquals(listOf(CallOutcome.INTERESTED, CallOutcome.SCHEDULED), cadence)
    }
}
