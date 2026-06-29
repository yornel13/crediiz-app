package com.project.vortex.callsagent.data.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/** [CrashReporter] backed by Firebase Crashlytics. */
@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {

    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun setUserId(id: String) {
        crashlytics.setUserId(id)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
}
