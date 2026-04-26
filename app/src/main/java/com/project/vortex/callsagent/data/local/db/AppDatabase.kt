package com.project.vortex.callsagent.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.project.vortex.callsagent.data.local.entity.ClientDismissalEntity
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import com.project.vortex.callsagent.data.local.entity.FollowUpEntity
import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import com.project.vortex.callsagent.data.local.entity.MissedCallEntity
import com.project.vortex.callsagent.data.local.entity.NoteEntity

@Database(
    entities = [
        ClientEntity::class,
        InteractionEntity::class,
        NoteEntity::class,
        FollowUpEntity::class,
        MissedCallEntity::class,
        ClientDismissalEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun interactionDao(): InteractionDao
    abstract fun noteDao(): NoteDao
    abstract fun followUpDao(): FollowUpDao
    abstract fun missedCallDao(): MissedCallDao
    abstract fun clientDismissalDao(): ClientDismissalDao

    companion object {
        const val DATABASE_NAME = "calls_agent.db"
    }
}
