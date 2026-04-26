package com.project.vortex.callsagent.presentation.clients

import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.ClientDismissal
import java.time.Instant

/**
 * A single row in the Recientes feed. Two flavors:
 *
 *  - [Called]: client was called inside the 24 h window. Renders the
 *    outcome-led `RecentClientCard`.
 *  - [Dismissed]: client was dismissed by the agent inside the 24 h
 *    window AND has not been undone. Renders `RecentDismissalCard`
 *    with the Deshacer button.
 *
 * If a client has both a recent call and a recent dismissal, the
 * dismissal wins (most recent agent intent). The dedupe lives in
 * the ViewModel.
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
}
