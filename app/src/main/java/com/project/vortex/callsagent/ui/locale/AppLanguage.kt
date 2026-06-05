package com.project.vortex.callsagent.ui.locale

/**
 * User-facing app language override. Mirrors the [com.project.vortex.callsagent.ui.theme.ThemeMode]
 * pattern: persisted by `.name`, resolved back with [fromKey], defaulting to
 * [SYSTEM] so the device locale wins unless the agent picks otherwise.
 *
 * The default app language (res/values) is English; Spanish lives in
 * res/values-es. [toLocaleTag] returns a bare language tag (no region) to
 * match those resource folders.
 */
enum class AppLanguage {
    /** Honor the device locale — do not force any locale. */
    SYSTEM,
    ENGLISH,
    SPANISH,
    ;

    /** BCP-47 language tag, or `null` for [SYSTEM] (meaning "no override"). */
    fun toLocaleTag(): String? = when (this) {
        SYSTEM -> null
        ENGLISH -> "en"
        SPANISH -> "es"
    }

    companion object {
        fun fromKey(key: String?): AppLanguage = when (key?.uppercase()) {
            ENGLISH.name -> ENGLISH
            SPANISH.name -> SPANISH
            else -> SYSTEM
        }
    }
}
