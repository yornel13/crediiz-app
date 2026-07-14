package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [FollowUpCadencePolicy.shouldKeepExistingFollowUp] — the
 * keep-vs-supersede decision PostCall's save applies to the client's
 * existing future PENDING follow-up.
 *
 * The invariant under test mirrors two calls-core behaviors:
 *  - keeping is only safe on a PURE re-confirmation (outcome targets the
 *    rung the client is already on) because a status-ADVANCING outcome
 *    triggers the BE-04 cascade server-side, which cancels the kept row;
 *  - any schedule change must supersede, because the sync contract has no
 *    reschedule push — a fresh creation ("latest schedule wins") is the
 *    only way to move the date.
 */
class FollowUpCadencePolicyTest {

    private val existingAt: Instant = Instant.parse("2026-07-20T19:00:00Z")
    private val differentAt: Instant = Instant.parse("2026-07-22T15:30:00Z")

    private fun keep(
        outcome: CallOutcome,
        status: ClientStatus?,
        existing: Instant?,
        new: Instant,
    ) = FollowUpCadencePolicy.shouldKeepExistingFollowUp(
        outcome = outcome,
        clientStatus = status,
        existingScheduledAt = existing,
        newScheduledAt = new,
    )

    @Test
    fun reconfirmation_with_untouched_schedule_keeps_the_existing_row() {
        // "Continúa interesado" on an INTERESTED client, picker untouched.
        assertTrue(keep(CallOutcome.INTERESTED, ClientStatus.INTERESTED, existingAt, existingAt))
        // "Continúa citado" on a CITED client, picker untouched.
        assertTrue(keep(CallOutcome.SCHEDULED, ClientStatus.CITED, existingAt, existingAt))
    }

    @Test
    fun reconfirmation_with_changed_schedule_supersedes() {
        assertFalse(keep(CallOutcome.INTERESTED, ClientStatus.INTERESTED, existingAt, differentAt))
        assertFalse(keep(CallOutcome.SCHEDULED, ClientStatus.CITED, existingAt, differentAt))
    }

    @Test
    fun status_advancing_outcome_always_supersedes_even_if_schedule_untouched() {
        // First-time SCHEDULED on an INTERESTED client moves the status to
        // CITED — the BE-04 cascade cancels the kept row server-side, so a
        // fresh creation MUST be pushed even when the date didn't change.
        assertFalse(keep(CallOutcome.SCHEDULED, ClientStatus.INTERESTED, existingAt, existingAt))
        // INTERESTED on a PENDING client (advance) with a pre-existing
        // admin-created follow-up: same rule.
        assertFalse(keep(CallOutcome.INTERESTED, ClientStatus.PENDING, existingAt, existingAt))
    }

    @Test
    fun no_existing_follow_up_always_creates() {
        assertFalse(keep(CallOutcome.INTERESTED, ClientStatus.INTERESTED, null, existingAt))
        assertFalse(keep(CallOutcome.SCHEDULED, ClientStatus.CITED, null, existingAt))
    }

    @Test
    fun unknown_client_status_fails_toward_creating() {
        // With no known status we cannot prove the outcome is a pure
        // re-confirmation — superseding is the safe default.
        assertFalse(keep(CallOutcome.INTERESTED, null, existingAt, existingAt))
    }

    @Test
    fun non_advancing_outcomes_never_keep() {
        // Defensive: the policy is only consulted for schedulesFollowUp
        // outcomes, but a null advance target must never report "keep".
        assertFalse(keep(CallOutcome.NOT_INTERESTED, ClientStatus.INTERESTED, existingAt, existingAt))
    }
}
