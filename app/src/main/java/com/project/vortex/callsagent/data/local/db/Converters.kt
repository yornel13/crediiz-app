package com.project.vortex.callsagent.data.local.db

import androidx.room.TypeConverter
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.FollowUpStatus
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

    // ─── CallOutcome ↔ String ─────────────────────────────────────────────────
    @TypeConverter fun callOutcomeToString(v: CallOutcome?): String? = v?.name
    @TypeConverter fun stringToCallOutcome(v: String?): CallOutcome? = v?.let { CallOutcome.valueOf(it) }

    // ─── FollowUpStatus ↔ String ──────────────────────────────────────────────
    @TypeConverter fun followUpStatusToString(v: FollowUpStatus): String = v.name
    @TypeConverter fun stringToFollowUpStatus(v: String): FollowUpStatus = FollowUpStatus.valueOf(v)

    // ─── NoteType ↔ String ────────────────────────────────────────────────────
    @TypeConverter fun noteTypeToString(v: NoteType): String = v.name
    @TypeConverter fun stringToNoteType(v: String): NoteType = NoteType.valueOf(v)

    // ─── SyncStatus ↔ String ──────────────────────────────────────────────────
    @TypeConverter fun syncStatusToString(v: SyncStatus): String = v.name
    @TypeConverter fun stringToSyncStatus(v: String): SyncStatus = SyncStatus.valueOf(v)

    // ─── Map<String, Any?> ↔ JSON String ──────────────────────────────────────
    @TypeConverter
    fun mapToJson(value: Map<String, Any?>?): String? =
        value?.let { mapAdapter.toJson(it) }

    @TypeConverter
    fun jsonToMap(value: String?): Map<String, Any?>? =
        value?.let { mapAdapter.fromJson(it) }
}
