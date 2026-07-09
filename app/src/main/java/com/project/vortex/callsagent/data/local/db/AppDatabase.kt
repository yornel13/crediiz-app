package com.project.vortex.callsagent.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import com.project.vortex.callsagent.data.local.entity.FollowUpEntity
import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity
import com.project.vortex.callsagent.data.local.entity.MissedCallEntity
import com.project.vortex.callsagent.data.local.entity.NoteEntity

@Database(
    entities = [
        ClientEntity::class,
        InteractionEntity::class,
        NoteEntity::class,
        FollowUpEntity::class,
        MissedCallEntity::class,
        LocalAgentStatusChangeEntity::class,
    ],
    // v12: ClientEntity.agentCallAttempts (per-agent attempt count from the
    // server, drives the "Sin llamar" vs "Para reintentar" split).
    // v13: NoteEntity author snapshot (authorId/Name/Role) — first MANUAL
    // migration (Migrations.kt). Since the identity-keyed-wipe model, Room
    // is NOT a disposable cache anymore: it holds un-synced PENDING rows
    // that survive logout, so destructive migration would destroy field
    // data. Every schema change from here on needs a hand-written step.
    version = 13,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun interactionDao(): InteractionDao
    abstract fun noteDao(): NoteDao
    abstract fun followUpDao(): FollowUpDao
    abstract fun missedCallDao(): MissedCallDao
    abstract fun localAgentStatusChangeDao(): LocalAgentStatusChangeDao

    companion object {
        const val DATABASE_NAME = "calls_agent.db"
    }
}
