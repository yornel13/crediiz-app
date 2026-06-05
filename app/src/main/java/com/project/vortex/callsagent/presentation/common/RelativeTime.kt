package com.project.vortex.callsagent.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.project.vortex.callsagent.R
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Centralized, localized relative-time formatting. Replaces the half-dozen
 * near-identical `formatRelative` helpers that were scattered across screens
 * (each with hardcoded English/Spanish literals). Compact style ("5m ago",
 * "in 2h") matching the original UI.
 *
 * These are `@Composable` so the strings re-resolve against the activity's
 * current locale (the activity is recreated on a language change, see
 * `LocaleAwareActivity`). For an "older than a week → absolute date" fallback,
 * callers handle the date branch themselves and delegate the sub-week range
 * here.
 */

/** "just now" / "5m ago" / "3h ago" / "2d ago" / "1w ago". */
@Composable
fun relativePast(instant: Instant, now: Instant = Instant.now()): String {
    val minutes = ChronoUnit.MINUTES.between(instant, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.reltime_just_now)
        minutes < MINUTES_PER_HOUR ->
            stringResource(R.string.reltime_minutes_ago, minutes.toInt())
        minutes < MINUTES_PER_DAY ->
            stringResource(R.string.reltime_hours_ago, (minutes / MINUTES_PER_HOUR).toInt())
        minutes < MINUTES_PER_WEEK ->
            stringResource(R.string.reltime_days_ago, (minutes / MINUTES_PER_DAY).toInt())
        else ->
            stringResource(R.string.reltime_weeks_ago, (minutes / MINUTES_PER_WEEK).toInt())
    }
}

/** "now" / "in 5m" / "in 3h" / "in 2d" / "in 1w". */
@Composable
fun relativeFuture(target: Instant, now: Instant = Instant.now()): String {
    val minutes = ChronoUnit.MINUTES.between(now, target)
    return when {
        minutes < 1 -> stringResource(R.string.reltime_now)
        minutes < MINUTES_PER_HOUR ->
            stringResource(R.string.reltime_in_minutes, minutes.toInt())
        minutes < MINUTES_PER_DAY ->
            stringResource(R.string.reltime_in_hours, (minutes / MINUTES_PER_HOUR).toInt())
        minutes < MINUTES_PER_WEEK ->
            stringResource(R.string.reltime_in_days, (minutes / MINUTES_PER_DAY).toInt())
        else ->
            stringResource(R.string.reltime_in_weeks, (minutes / MINUTES_PER_WEEK).toInt())
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 60L * 24
private const val MINUTES_PER_WEEK = 60L * 24 * 7
