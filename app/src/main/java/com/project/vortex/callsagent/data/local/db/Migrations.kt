package com.project.vortex.callsagent.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written Room migrations.
 *
 * Required since the identity-keyed-wipe model (v1.0.7): Room persists
 * un-synced PENDING rows across logouts, so `fallbackToDestructiveMigration`
 * would destroy field data on every schema bump. The fallback stays
 * registered as a last resort for downgrade/corruption, but every forward
 * schema change MUST ship its migration here.
 */

/** v13: author snapshot on notes (who wrote it — N:M shared clients). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN authorId TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN authorName TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN authorRole TEXT")
    }
}

/** All migrations, in order — registered by DatabaseModule. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_12_13)
