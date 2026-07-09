package com.project.vortex.callsagent.presentation.clients

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.common.BusinessConfig
import com.project.vortex.callsagent.domain.model.Client
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One slice of the "Untouched" feed, grouped by [Client.assignedAt].
 *
 * Buckets are ordered by [sortKey] descending so the **freshest
 * assignments surface at the top** of the list and the oldest sink
 * to the bottom.
 *
 * Buckets (high-level):
 *  - **Older** — `assignedAt` ≥ 8 days ago or `null`.
 *  - **Specific weekday** — between 3 and 7 days ago, labeled like
 *    "Assigned Fri 8 May" (rendered in the current locale). Empty days
 *    are skipped.
 *  - **2 days ago / Yesterday / Today**.
 *
 * The label is locale-resolved at render time via [resolveLabel] — the
 * bucket only carries a [labelRes] (fixed buckets) or a [date] (the dynamic
 * weekday bucket), never a pre-formatted string.
 *
 * Equality is by [key] so consecutive identical buckets dedupe when
 * the map is built (matters for the weekday bucket, where two clients
 * assigned on the same day land in the same bucket).
 */
data class PendingDateBucket(
    /** Stable map key — used for Compose `key()` to keep recompositions cheap. */
    val key: String,
    /** String resource for fixed buckets, or `null` for the dynamic [date] bucket. */
    @StringRes val labelRes: Int?,
    /** Set only for the specific-weekday bucket; drives locale-aware formatting. */
    val date: LocalDate?,
    /** Sort order, ascending. Older buckets get smaller values. */
    val sortKey: Long,
) {
    companion object {

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

        /** Eldest possible bucket — always sorts last. */
        val earlier: PendingDateBucket = PendingDateBucket(
            key = "EARLIER",
            labelRes = R.string.clients_bucket_earlier,
            date = null,
            sortKey = Long.MIN_VALUE,
        )

        private fun specificDay(date: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "DAY:${date}",
                labelRes = null,
                date = date,
                sortKey = date.toEpochDay(),
            )

        private fun dayBeforeYesterday(today: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "DAY_BEFORE_YESTERDAY",
                labelRes = R.string.clients_bucket_day_before_yesterday,
                date = null,
                sortKey = today.minusDays(2).toEpochDay(),
            )

        private fun yesterday(today: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "YESTERDAY",
                labelRes = R.string.clients_bucket_yesterday,
                date = null,
                sortKey = today.minusDays(1).toEpochDay(),
            )

        private fun today(today: LocalDate): PendingDateBucket =
            PendingDateBucket(
                key = "TODAY",
                labelRes = R.string.clients_bucket_today,
                date = null,
                sortKey = today.toEpochDay(),
            )
    }
}

/**
 * Locale-resolved section label. For the dynamic weekday bucket it formats
 * "Assigned Fri 8 May" / "Asignados el vie 8 may" in the current locale;
 * for fixed buckets it resolves [labelRes]. The weekday keeps the locale's
 * natural casing — it sits mid-sentence after the "Assigned" prefix.
 */
@Composable
fun PendingDateBucket.resolveLabel(): String {
    val date = date
    if (date != null) {
        val locale = Locale.getDefault()
        val weekday = date.dayOfWeek
            .getDisplayName(TextStyle.SHORT, locale)
            .trimEnd('.')
        val rest = date.format(DateTimeFormatter.ofPattern("d MMM", locale))
        return stringResource(R.string.clients_bucket_assigned_on, "$weekday $rest")
    }
    return labelRes?.let { stringResource(it) }.orEmpty()
}

/**
 * Splits a flat list of PENDING-never-called clients into date
 * buckets ordered newest-first. Within each bucket, clients are
 * sorted by [Client.assignedAt] descending (most recent on top);
 * a `null` assignedAt sinks to the end of its bucket.
 *
 * Returns a `LinkedHashMap` so iteration order matches insertion
 * order (newest to oldest), giving the UI a stable render contract.
 */
fun groupPendingNeverCalledByAssignedDate(
    clients: List<Client>,
    // Default to business clock so the bucket boundaries
    // ("today" / "yesterday" / "older") line up with the admin's
    // calendar. Callers in tests can still inject a fixed zone.
    // See BusinessConfig.
    zone: ZoneId = BusinessConfig.BUSINESS_TIMEZONE,
): Map<PendingDateBucket, List<Client>> {
    if (clients.isEmpty()) return emptyMap()
    val today = LocalDate.now(zone)
    val raw = clients.groupBy { client ->
        PendingDateBucket.forAssignedAt(client.assignedAt, today, zone)
    }
    // Sort buckets by sortKey descending (newest first). Inside each
    // bucket, most recent assignment on top; compareByDescending
    // treats null as smallest, so legacy null rows sink to the end.
    val newestFirst = compareByDescending<Client> { it.assignedAt }
    val sorted = LinkedHashMap<PendingDateBucket, List<Client>>(raw.size)
    raw.entries
        .sortedByDescending { it.key.sortKey }
        .forEach { (bucket, list) -> sorted[bucket] = list.sortedWith(newestFirst) }
    return sorted
}
