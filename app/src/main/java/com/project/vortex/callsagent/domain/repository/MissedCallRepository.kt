package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.common.enums.MissedCallReason
import com.project.vortex.callsagent.domain.model.MissedCall
import kotlinx.coroutines.flow.Flow

interface MissedCallRepository {

    suspend fun log(
        phoneNumber: String,
        matchedClientId: String?,
        reason: MissedCallReason,
    )

    fun observeUnacknowledged(): Flow<List<MissedCall>>
    fun observeUnacknowledgedCount(): Flow<Int>

    suspend fun markAcknowledged(id: String)
    suspend fun markAllAcknowledged()
}
