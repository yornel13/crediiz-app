package com.project.vortex.callsagent.data.crash

/**
 * Thin abstraction over the crash-reporting SDK (Crashlytics). Keeps the rest
 * of the app — and especially [CrashContextReporter] — free of Firebase types
 * so the context-reporting logic is unit-testable with a fake.
 */
interface CrashReporter {
    /** Associate subsequent crashes with this agent (empty string clears it). */
    fun setUserId(id: String)

    /** Add a breadcrumb to the crash log of the current session. */
    fun log(message: String)

    /** Attach a custom key/value shown on every crash from now on. */
    fun setKey(key: String, value: String)
}
