package com.project.vortex.callsagent.presentation.clients

import com.project.vortex.callsagent.domain.model.AgentStatusChangeLocal
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.ClientDismissal
import java.time.Instant

/**
 * A single row in the Recientes feed. Three flavors:
 *
 *  - [Called]: client was called inside the 24 h window. Renders the
 *    outcome-led `RecentClientCard`.
 *  - [Dismissed]: client was dismissed by the agent inside the 24 h
 *    window AND has not been undone. Renders `RecentDismissalCard`
 *    with the Deshacer button.
 *  - [StatusChanged]: agent moved the client to a new status WITHOUT
 *    a call (e.g. WhatsApp opt-out → DO_NOT_CALL). Renders
 *    `RecentStatusChangeCard`. Distinct visual so the agent never
 *    confuses it with an actual call.
 *
 * When the same client has multiple recent actions, the most recent
 * agent intent wins. Dedupe lives in the ViewModel.
 */
sealed interface RecentEntry {
    val client: Client
    val timestamp: Instant
    val sortKey: String

    data class Called(
        override val client: Client,
        override val timestamp: Instant,
    ) : RecentEntry {
        override val sortKey: String get() = "call:${client.id}"
    }

    data class Dismissed(
        override val client: Client,
        override val timestamp: Instant,
        val dismissal: ClientDismissal,
    ) : RecentEntry {
        override val sortKey: String get() = "dis:${client.id}"
    }

    data class StatusChanged(
        override val client: Client,
        override val timestamp: Instant,
        val change: AgentStatusChangeLocal,
    ) : RecentEntry {
        override val sortKey: String get() = "chg:${change.id}"
    }
}
