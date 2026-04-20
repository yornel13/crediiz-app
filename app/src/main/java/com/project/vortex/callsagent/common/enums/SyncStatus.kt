package com.project.vortex.callsagent.common.enums

/**
 * Tracks sync state of records created locally on the device.
 * PENDING: needs to be pushed to server.
 * SYNCED: already acknowledged by server.
 */
enum class SyncStatus {
    PENDING,
    SYNCED,
}
