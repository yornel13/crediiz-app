package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.NoteType
import java.time.Instant

/**
 * Unified, read-only event in the per-client activity timeline shown
 * on PreCall. Each variant maps onto an existing domain record
 * (Interaction, Note, FollowUp) and exposes only the fields the
 * timeline UI actually renders.
 *
 * Why a sealed interface and not just pass the underlying records?
 *  - The timeline UI does NOT want to know about persistence concerns
 *    (mobileSyncId, syncStatus, disconnectCause, etc.). Keep the
 *    viewmodel-facing surface clean.
 *  - A single sorted `List<ActivityEvent>` is trivial to render in a
 *    LazyColumn; merging heterogeneous lists in the UI layer would
 *    require N parallel `items()` calls and broken ordering.
 *  - New event types (status changes, dismissals, wrong-number
 *    strikes) drop in here as new variants without disturbing the
 *    timeline composable.
 *
 * `occurredAt` is the canonical sort key — descending (most recent
 * first). The mapper in `ActivityRepository` is responsible for
 * choosing the right timestamp from each underlying record
 * (`callStartedAt`, `deviceCreatedAt`, `scheduledAt`, etc.).
 */
sealed interface ActivityEvent {
    val occurredAt: Instant
    val agentId: String?

    /** A phone-call attempt — derived from [Interaction]. */
    data class Call(
        override val occurredAt: Instant,
        override val agentId: String?,
        val durationSeconds: Int,
        val outcome: CallOutcome,
    ) : ActivityEvent

    /** An agent-authored or system-generated note — derived from [Note]. */
    data class NoteEntry(
        override val occurredAt: Instant,
        override val agentId: String?,
        val content: String,
        val type: NoteType,
    ) : ActivityEvent

    /**
     * A scheduled follow-up — derived from [FollowUp]. We surface only
     * the FUTURE / today follow-ups here as "agenda promises";
     * fulfilled ones become a [Call] row instead.
     */
    data class FollowUpScheduled(
        override val occurredAt: Instant,
        override val agentId: String?,
        val scheduledFor: Instant,
    ) : ActivityEvent

    /**
     * Marker that the client was originally imported into the system.
     * Provides a "story-start" anchor so timelines never look empty
     * — even for first-contact clients we show "Imported X days ago".
     */
    data class LeadImported(
        override val occurredAt: Instant,
    ) : ActivityEvent {
        override val agentId: String? = null
    }

    /**
     * Anchor entry marking when the admin assigned this client to the
     * currently-logged-in agent. Derived from `Client.assignedAt` —
     * not from a separate table. Renders as a minimal centered row in
     * the timeline (no card), similar to [LeadImported], because it's
     * a fact about the agent↔client relationship rather than an
     * agent-authored action.
     *
     * Emitted only when `Client.assignedAt` is non-null (legacy rows
     * without the field stay invisible — better than guessing).
     */
    data class AssignedToAgent(
        override val occurredAt: Instant,
    ) : ActivityEvent {
        override val agentId: String? = null
    }
}
