package com.project.vortex.callsagent.presentation.clients

import com.project.vortex.callsagent.domain.model.Client
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One slice of the "Sin llamar" feed, grouped by [Client.assignedAt].
 *
 * Buckets are ordered by [sortKey] ascending so the **oldest clients
 * surface at the top** of the list and the freshest assignments sink
 * to the bottom (agent should attend the aging tail first).
 *
 * Buckets (high-level):
 *  - **Más antiguos** — `assignedAt` ≥ 8 days ago or `null`.
 *  - **Specific weekday** — between 3 and 7 days ago, labeled like
 *    "Vie 8 mayo". Empty days are skipped.
 *  - **Antier** — exactly 2 days ago.
 *  - **Ayer** — exactly 1 day ago.
 *  - **Hoy** — today (in the device timezone).
 *
 * Equality is by [key] so consecutive identical buckets dedupe when
 * the map is built (matters for `SpecificDay`, where two clients
 * assigned on the same day land in the same bucket).
 */
data class PendingDateBucket(
    /** Stable map key — used for Compose `key()` to keep recompositions cheap. */
    val key: String,
    /** Spanish label rendered in the section header. */
    val label: String,
    /** Sort order, ascending. Older buckets get smaller values. */
    val sortKey: Long,
) {
    companion object {

        private val dayLabelFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "PA"))

        /**
         * Routes a single client's [assignedAt] to the bucket it
         * belongs in, evaluated relative to [today] in [zone].
         *
         * Pure function — call-sites pass `LocalDate.now(zone)` once
         * per grouping pass to keep all classifications consistent
         * within the same emission (and trivially testable).
         */
        fun forAssignedAt(
            assignedAt: Instant?,
            today: LocalDate,
            zone: ZoneId,
        ): PendingDateBucket {
            val date = assignedAt?.atZone(zone)?.toLocalDate()
                ?: return earlier
            val daysAgo = ChronoUnit.DAYS.between(date, today)
            return when {
                daysAgo == 0L -> today(today)
                daysAgo == 1L -> yesterday(today)
                daysAgo == 2L -> dayBeforeYesterday(today)
                daysAgo in 3..7 -> specificDay(date)
                else -> earlier
            }
        }

        /** Eldest possible bucket — always sorts first. */
        val earlier: PendingDateBucket = PendingDateBucket(
            key = "EARLIER",
            label = "Más antiguos",
            sortKey = Long.MIN_VALUE,
        )

        private fun specificDay(date: LocalDate): PendingDateBucket {
            // "Vie 8 de mayo" — weekday short + day + month, Spanish.
            val weekday = date.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale("es", "PA"))
                .replaceFirstChar { it.uppercase(Locale("es", "PA")) }
                .trimEnd('.')
            return PendingDateBucket(
                key = "DAY:${date}",
                label = "$weekday ${date.format(dayLabelFormatter)}",
                sortKey = date.toEpochDay(),
            )
        }

        private fun dayBeforeYesterday(today: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "DAY_BEFORE_YESTERDAY",
                label = "Antier",
                sortKey = today.minusDays(2).toEpochDay(),
            )

        private fun yesterday(today: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "YESTERDAY",
                label = "Ayer",
                sortKey = today.minusDays(1).toEpochDay(),
            )

        private fun today(today: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "TODAY",
                label = "Hoy",
                sortKey = today.toEpochDay(),
            )
    }
}

/**
 * Splits a flat list of PENDING-never-called clients into date
 * buckets ordered oldest-first. Within each bucket, clients keep
 * their incoming order (driven by the DAO's `ORDER BY queueOrder ASC`).
 *
 * Returns a `LinkedHashMap` so iteration order matches insertion
 * order (oldest to newest), giving the UI a stable render contract.
 */
fun groupPendingNeverCalledByAssignedDate(
    clients: List<Client>,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<PendingDateBucket, List<Client>> {
    if (clients.isEmpty()) return emptyMap()
    val today = LocalDate.now(zone)
    val raw = clients.groupBy { client ->
        PendingDateBucket.forAssignedAt(client.assignedAt, today, zone)
    }
    // Sort by sortKey ascending (oldest first), preserve insertion
    // order inside each bucket via LinkedHashMap.
    val sorted = LinkedHashMap<PendingDateBucket, List<Client>>(raw.size)
    raw.entries
        .sortedBy { it.key.sortKey }
        .forEach { (bucket, list) -> sorted[bucket] = list }
    return sorted
}
