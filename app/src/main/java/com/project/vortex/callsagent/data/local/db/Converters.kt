package com.project.vortex.callsagent.data.local.db

import androidx.room.TypeConverter
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.MissedCallReason
import com.project.vortex.callsagent.common.enums.QuotationValidation
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

/**
 * Type converters for Room. Handles:
 * - Instant <-> Long (millis)
 * - Enum <-> String (for all app enums)
 * - Map<String, Any?> <-> String (JSON) for extraData
 */
class Converters {

    // Use reflection-based adapter for Map<String, Any?> since it's dynamic
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    // ─── Instant ↔ Long (millis) ──────────────────────────────────────────────
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // ─── ClientStatus ↔ String ────────────────────────────────────────────────
    @TypeConverter fun clientStatusToString(v: ClientStatus): String = v.name
    @TypeConverter fun stringToClientStatus(v: String): ClientStatus = ClientStatus.valueOf(v)

    // ─── RemovalReason ↔ String (nullable — only set when REMOVED) ────────────
    @TypeConverter fun removalReasonToString(v: RemovalReason?): String? = v?.name
    @TypeConverter fun stringToRemovalReason(v: String?): RemovalReason? =
        v?.let { RemovalReason.valueOf(it) }

    // ─── QuotationValidation ↔ String (nullable — null = no quotation) ────────
    @TypeConverter fun quotationValidationToString(v: QuotationValidation?): String? = v?.name
    @TypeConverter fun stringToQuotationValidation(v: String?): QuotationValidation? =
        v?.let { QuotationValidation.valueOf(it) }

    // ─── CallOutcome ↔ String ─────────────────────────────────────────────────
    @TypeConverter fun callOutcomeToString(v: CallOutcome?): String? = v?.name
    @TypeConverter fun stringToCallOutcome(v: String?): CallOutcome? = v?.let { CallOutcome.valueOf(it) }

    // ─── FollowUpStatus ↔ String ──────────────────────────────────────────────
    @TypeConverter fun followUpStatusToString(v: FollowUpStatus): String = v.name
    // Fall back to PENDING on any unknown value so a future backend status (or
    // a value written by a newer build) can't crash the read. Mirrors the
    // defensive parse in FollowUpMapper.
    @TypeConverter fun stringToFollowUpStatus(v: String): FollowUpStatus =
        runCatching { FollowUpStatus.valueOf(v) }.getOrDefault(FollowUpStatus.PENDING)

    // ─── NoteType ↔ String ────────────────────────────────────────────────────
    @TypeConverter fun noteTypeToString(v: NoteType): String = v.name
    @TypeConverter fun stringToNoteType(v: String): NoteType = NoteType.valueOf(v)

    // ─── SyncStatus ↔ String ──────────────────────────────────────────────────
    @TypeConverter fun syncStatusToString(v: SyncStatus): String = v.name
    @TypeConverter fun stringToSyncStatus(v: String): SyncStatus = SyncStatus.valueOf(v)

    // ─── CallDirection ↔ String ───────────────────────────────────────────────
    @TypeConverter fun callDirectionToString(v: CallDirection): String = v.name
    @TypeConverter fun stringToCallDirection(v: String): CallDirection = CallDirection.valueOf(v)

    // ─── MissedCallReason ↔ String ────────────────────────────────────────────
    @TypeConverter fun missedCallReasonToString(v: MissedCallReason): String = v.name
    @TypeConverter fun stringToMissedCallReason(v: String): MissedCallReason = MissedCallReason.valueOf(v)

    // ─── Map<String, Any?> ↔ JSON String ──────────────────────────────────────
    @TypeConverter
    fun mapToJson(value: Map<String, Any?>?): String? =
        value?.let { mapAdapter.toJson(it) }

    @TypeConverter
    fun jsonToMap(value: String?): Map<String, Any?>? =
        value?.let { mapAdapter.fromJson(it) }
}
