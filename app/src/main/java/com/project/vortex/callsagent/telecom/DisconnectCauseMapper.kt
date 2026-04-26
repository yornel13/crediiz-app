package com.project.vortex.callsagent.telecom

import android.telecom.DisconnectCause
import com.project.vortex.callsagent.common.enums.CallOutcome

/**
 * Translates a Telecom [DisconnectCause] into our internal [CallOutcome].
 *
 * Returns `null` when the cause does NOT carry enough information to
 * choose an outcome (e.g. either the agent or the client hung up
 * cleanly — the call's nature is determined by what was said, not by
 * who pressed end). Caller should fall back to a neutral default and
 * let the agent confirm on Post-Call.
 *
 * Mapping table:
 *
 * | Telecom code | → CallOutcome    | Why |
 * |---|---|---|
 * | `BUSY`       | `BUSY`           | Carrier returned a busy tone. |
 * | `CANCELED`   | `NO_ANSWER`      | Outgoing call cancelled before connect. |
 * | `MISSED`     | `NO_ANSWER`      | Call rang but never answered. |
 * | `REJECTED`   | `NOT_INTERESTED` | Remote side actively declined the call. |
 * | `ERROR`      | sub-cause-based  | See [mapErrorSubcause]. |
 * | `LOCAL`      | `null`           | Agent hung up — outcome is conversational. |
 * | `REMOTE`     | `null`           | Client hung up — same. |
 * | `OTHER`/null | `null`           | Unknown — fall back to default. |
 *
 * Sub-cause matching for `ERROR` is best-effort string contains, since
 * Telecom's `reason` strings are not stable across OEMs / carriers.
 * Refine once we have field data from the corporate SIM.
 */
object DisconnectCauseMapper {

    fun toOutcome(cause: DisconnectCause?): CallOutcome? = when (cause?.code) {
        null -> null
        DisconnectCause.BUSY -> CallOutcome.BUSY
        DisconnectCause.CANCELED -> CallOutcome.NO_ANSWER
        DisconnectCause.MISSED -> CallOutcome.NO_ANSWER
        DisconnectCause.REJECTED -> CallOutcome.NOT_INTERESTED
        DisconnectCause.ERROR -> mapErrorSubcause(cause)
        DisconnectCause.LOCAL,
        DisconnectCause.REMOTE,
        DisconnectCause.OTHER -> null
        else -> null
    }

    private fun mapErrorSubcause(cause: DisconnectCause): CallOutcome? {
        val reason = cause.reason?.lowercase().orEmpty()
        val description = cause.description?.toString()?.lowercase().orEmpty()
        val haystack = "$reason $description"
        return when {
            "invalid_number" in haystack || "unallocated" in haystack ->
                CallOutcome.INVALID_NUMBER
            "busy" in haystack -> CallOutcome.BUSY
            else -> null
        }
    }
}
