package com.project.vortex.callsagent.di

import android.content.Context
import androidx.room.Room
import com.project.vortex.callsagent.data.local.db.AppDatabase
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.ClientDismissalDao
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
            // **No `fallbackToDestructiveMigration()`** — production
            // policy. v1.0 ships starting from a clean Room schema on
            // every fresh install; we never bump `@Database.version`
            // without writing an explicit `Migration`. If a schema
            // mismatch ever happens at runtime, Room throws
            // `IllegalStateException` on first open — that's the
            // intended fail-fast signal during QA, not silent data
            // wipe in the field.
            .build()

    @Provides fun provideClientDao(db: AppDatabase): ClientDao = db.clientDao()
    @Provides fun provideInteractionDao(db: AppDatabase): InteractionDao = db.interactionDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideFollowUpDao(db: AppDatabase): FollowUpDao = db.followUpDao()
    @Provides fun provideMissedCallDao(db: AppDatabase): MissedCallDao = db.missedCallDao()
    @Provides fun provideClientDismissalDao(db: AppDatabase): ClientDismissalDao = db.clientDismissalDao()
    @Provides
    fun provideLocalAgentStatusChangeDao(db: AppDatabase): LocalAgentStatusChangeDao =
        db.localAgentStatusChangeDao()
}
