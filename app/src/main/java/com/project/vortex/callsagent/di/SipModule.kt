package com.project.vortex.callsagent.di

import android.content.Context
import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipConfig
import com.project.vortex.callsagent.data.sip.auth.SipCredentialsProvider
import com.project.vortex.callsagent.data.voip.VoipAccountRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the SIP engine into Hilt.
 *
 * This module is the **single point** where Hilt and the engine meet.
 * The engine itself ([LinphoneCoreManager], [SipConfig], etc.) is
 * Hilt-agnostic — see `docs/SIP_ENGINE_BOUNDARIES.md`. To port the
 * engine to another app, drop the `data/sip/` package and write a new
 * module like this one.
 *
 * **Phase B (active):** credentials come from the backend via
 * [VoipAccountRepository]. The previous Phase A provider that read
 * from `BuildConfig` (`SIP_SERVER` / `SIP_USER` / `SIP_PASSWORD`)
 * has been removed — the build script no longer generates those
 * fields and the entries were stripped from `local.properties`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SipModule {

    /**
     * Reads the SIP config from the cached VoIP account. Returns null
     * via [error] only when the cache is empty AND the registration
     * was triggered out-of-band — the call orchestrator already gates
     * `startCall` behind `voipAvailability == Available`, so callers
     * of [SipCredentialsProvider.current] after a successful `register`
     * will always find credentials.
     */
    @Provides
    @Singleton
    fun provideSipCredentialsProvider(
        voipRepository: VoipAccountRepository,
    ): SipCredentialsProvider = object : SipCredentialsProvider {
        override suspend fun current(): SipConfig =
            voipRepository.current()
                ?: error(
                    "No VoIP credentials cached — cannot register SIP. " +
                        "Caller should observe VoipAvailability and only " +
                        "trigger SIP flows when Available.",
                )
    }

    @Provides
    @Singleton
    fun provideLinphoneCoreManager(
        @ApplicationContext context: Context,
        credentialsProvider: SipCredentialsProvider,
    ): LinphoneCoreManager = LinphoneCoreManager(context, credentialsProvider)
}
