package com.project.vortex.callsagent.common.enums

/**
 * Why an incoming call ended without the agent talking to the caller.
 *
 * - [REJECTED]: agent tapped Reject on the incoming UI.
 * - [NOT_ANSWERED]: caller hung up while ringing before the agent answered.
 * - [BUSY_OTHER_CALL]: another call was active; ConnectionService
 *   short-circuited the new connection with `DisconnectCause.BUSY`.
 */
enum class MissedCallReason {
    REJECTED,
    NOT_ANSWERED,
    BUSY_OTHER_CALL,
}
