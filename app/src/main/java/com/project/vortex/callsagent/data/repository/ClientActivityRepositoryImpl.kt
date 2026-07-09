package com.project.vortex.callsagent.data.repository

import android.util.Log
import com.project.vortex.callsagent.data.local.db.InteractionDao
import com.project.vortex.callsagent.data.local.db.NoteDao
import com.project.vortex.callsagent.data.mapper.toEntityOrNull
import com.project.vortex.callsagent.data.remote.api.ClientsApi
import com.project.vortex.callsagent.domain.repository.ClientActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClientActivityRepo"

/**
 * One network call per hydration, fanned out to both DAOs. Kept separate
 * from Note/Interaction repositories on purpose: those own their table's
 * lifecycle; this class owns the cross-table "rebuild the cache from the
 * server" concern and is the only consumer of the activity endpoint.
 */
@Singleton
class ClientActivityRepositoryImpl @Inject constructor(
    private val api: ClientsApi,
    private val noteDao: NoteDao,
    private val interactionDao: InteractionDao,
) : ClientActivityRepository {

    override suspend fun hydrate(clientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val activity = api.getActivity(clientId).data

            val notes = activity.notes.mapNotNull { it.toEntityOrNull() }
            val interactions = activity.interactions.mapNotNull { it.toEntityOrNull() }
            val skipped =
                (activity.notes.size - notes.size) + (activity.interactions.size - interactions.size)
            if (skipped > 0) {
                // Unknown enum value or unparseable date from a newer
                // backend — the defensive mappers dropped those rows.
                Log.w(TAG, "Hydration skipped $skipped unmappable activity rows for $clientId")
            }

            // insert-if-absent: PENDING local edits and local-only columns
            // (confirmedByAgent) are never clobbered. See the DAO KDocs.
            if (notes.isNotEmpty()) noteDao.insertIfAbsent(notes)
            if (interactions.isNotEmpty()) interactionDao.insertIfAbsent(interactions)
        }
    }
}
