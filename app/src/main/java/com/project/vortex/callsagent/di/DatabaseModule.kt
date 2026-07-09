package com.project.vortex.callsagent.di

import android.content.Context
import androidx.room.Room
import com.project.vortex.callsagent.data.local.db.ALL_MIGRATIONS
import com.project.vortex.callsagent.data.local.db.AppDatabase
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.FollowUpDao
import com.project.vortex.callsagent.data.local.db.InteractionDao
import com.project.vortex.callsagent.data.local.db.LocalAgentStatusChangeDao
import com.project.vortex.callsagent.data.local.db.MissedCallDao
import com.project.vortex.callsagent.data.local.db.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            // Forward schema changes ship hand-written migrations (see
            // Migrations.kt): since the identity-keyed-wipe model, Room
            // holds un-synced PENDING rows across logouts, so destroying
            // it on a version bump would lose field data. The destructive
            // fallback remains ONLY as a last resort for downgrades or a
            // missing-migration bug — never as the upgrade strategy.
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideClientDao(db: AppDatabase): ClientDao = db.clientDao()
    @Provides fun provideInteractionDao(db: AppDatabase): InteractionDao = db.interactionDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideFollowUpDao(db: AppDatabase): FollowUpDao = db.followUpDao()
    @Provides fun provideMissedCallDao(db: AppDatabase): MissedCallDao = db.missedCallDao()
    @Provides
    fun provideLocalAgentStatusChangeDao(db: AppDatabase): LocalAgentStatusChangeDao =
        db.localAgentStatusChangeDao()
}
