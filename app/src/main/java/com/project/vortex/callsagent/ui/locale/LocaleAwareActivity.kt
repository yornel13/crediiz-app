package com.project.vortex.callsagent.ui.locale

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project.vortex.callsagent.data.local.preferences.SettingsPreferences
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Base for every UI-hosting activity. Applies the agent's chosen app language
 * (see [AppLanguage]) by wrapping the base context in [attachBaseContext], and
 * recreates the activity when the preference changes so `stringResource`
 * lookups pick up the new locale.
 *
 * Concrete subclasses keep their own `@AndroidEntryPoint` annotation and pass
 * their injected [SettingsPreferences] into [observeLocaleChanges]. The
 * annotation is intentionally NOT on this abstract class — Hilt requires it on
 * the leaf. We take the preferences as a parameter (rather than an abstract
 * `val`) because Dagger field injection cannot target a `protected` field.
 */
abstract class LocaleAwareActivity : ComponentActivity() {

    /**
     * Locale tag this instance was actually built with (read synchronously in
     * [attachBaseContext]). Compared against later emissions so we recreate
     * exactly once on a real change and never loop on the initial value.
     */
    private var appliedTag: String? = null

    override fun attachBaseContext(newBase: Context) {
        // Runs before onCreate and before Hilt injection — read the persisted
        // tag through the static, cache-backed accessor instead of the injected
        // instance.
        val tag = SettingsPreferences.readLanguageTagBlocking(newBase)
        appliedTag = tag
        super.attachBaseContext(LocaleContext.wrap(newBase, tag))
    }

    /**
     * Recreates the activity when the language preference changes. Call from
     * the subclass's `onCreate` (after `super.onCreate`), passing the injected
     * [settingsPreferences] (available by then).
     */
    protected fun observeLocaleChanges(settingsPreferences: SettingsPreferences) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsPreferences.appLanguageFlow
                    .map { it.toLocaleTag() }
                    .distinctUntilChanged()
                    .collect { tag ->
                        // The first emission equals appliedTag → no-op. Only a
                        // genuine user change triggers a single recreate().
                        if (tag != appliedTag) recreate()
                    }
            }
        }
    }
}
