package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.MissedCallReason
import java.time.Instant

/**
 * Local log of incoming calls that the agent did NOT answer (rejected,
 * timed-out while ringing, or auto-rejected because another call was
 * active). Used to populate the missed-call banner in Clients tab so
 * the agent can return them as outbound calls.
 *
 * **Not synced.** This is a device-only ledger; the backend already sees
 * answered incoming calls via `InteractionEntity(direction=INBOUND)`.
 * Missed calls are an agent UX aid, not business data.
 */
@Entity(tableName = "missed_calls")
data class MissedCallEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    /** Non-null only if the calling number matches an assigned client. */
    val matchedClientId: String?,
    val reason: MissedCallReason,
    val occurredAt: Instant,
    val acknowledged: Boolean = false,
)
