package com.project.vortex.callsagent.ui.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Pure helper that produces a [Context] whose [android.content.res.Resources]
 * resolve against a forced locale. Used from `attachBaseContext` so the whole
 * activity (and its Compose tree) renders in the chosen language.
 *
 * No Android framework state is required to call it, which keeps it trivially
 * unit-testable and safe to invoke before Hilt injection completes.
 */
object LocaleContext {

    /**
     * Wraps [base] so its resources resolve against [tag].
     *
     * @param tag a bare BCP-47 language tag ("en", "es") or `null`/blank for
     *   SYSTEM, in which case [base] is returned untouched so the device
     *   configuration flows through naturally.
     */
    fun wrap(base: Context, tag: String?): Context {
        if (tag.isNullOrBlank()) return base

        val locale = Locale.forLanguageTag(tag)
        // Keep non-resource locale-sensitive code (date/number formatting,
        // String.format) consistent with the chosen UI language, not just
        // resource lookups.
        Locale.setDefault(locale)

        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)

        val config = Configuration(base.resources.configuration).apply {
            setLocales(localeList)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
