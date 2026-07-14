package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import java.time.Instant

/**
 * Outcomes that keep the client inside the follow-up cadence: picking one
 * REQUIRES scheduling the next contact (date + time) in PostCall, and it
 * stays selectable at the client's CURRENT funnel rung as a re-confirmation
 * ("Continúa interesado" / "Continúa citado") — re-recording the rung is not
 * a no-op precisely because it refreshes the next scheduled contact.
 *
 * SOLD is deliberately excluded: it is a terminal advance with no next
 * contact, so re-offering it at its own level stays hidden (see
 * [OutcomeVisibilityPolicy]).
 */
val CallOutcome.schedulesFollowUp: Boolean
    get() = this == CallOutcome.INTERESTED || this == CallOutcome.SCHEDULED

/**
 * Decides what PostCall's save must do with the client's existing future
 * PENDING follow-up when the agent confirms a [schedulesFollowUp] outcome.
 *
 * The rule exists because of TWO backend behaviors that the app must mirror,
 * not fight (calls-core):
 *
 *  1. `FollowUpsService.create` implements "the latest schedule wins": it
 *     cancels any prior PENDING of the (client, agent) pair before inserting.
 *     Pushing a new creation therefore IS the reschedule — no cancel push
 *     exists (the sync contract only carries creations and completions).
 *  2. `ClientsService.changeStatus` cascades "cancel all pending follow-ups"
 *     whenever the client LANDS ON any status other than INTERESTED (BE-04).
 *     A status-ADVANCING outcome (e.g. first-time SCHEDULED moving
 *     INTERESTED → CITED) will cancel the kept row server-side, so keeping
 *     it locally would strand the client off the agenda after the next pull.
 *     A pure re-confirmation (outcome targeting the rung the client is
 *     already on) no-ops in `changeStatus` and never reaches the cascade.
 *
 * Therefore the existing row may be KEPT only when BOTH hold: the outcome is
 * a pure re-confirmation of the client's current status, AND the agent left
 * the pre-filled schedule untouched. Every other combination must create a
 * fresh follow-up (superseding the old one locally and — via rule 1 —
 * remotely).
 */
object FollowUpCadencePolicy {

    fun shouldKeepExistingFollowUp(
        outcome: CallOutcome,
        clientStatus: ClientStatus?,
        existingScheduledAt: Instant?,
        newScheduledAt: Instant,
    ): Boolean {
        val isReconfirmation =
            outcome.advanceTargetStatus != null &&
                outcome.advanceTargetStatus == clientStatus
        return isReconfirmation && existingScheduledAt == newScheduledAt
    }
}
