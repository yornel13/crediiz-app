package com.project.vortex.callsagent.di

import android.content.Context
import com.project.vortex.callsagent.BuildConfig
import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipConfig
import com.project.vortex.callsagent.data.sip.auth.SipCredentialsProvider
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
 * Phase A: credentials are read from `BuildConfig` (sourced from
 * `local.properties` at build time). Phase B will replace
 * [provideSipCredentialsProvider] with a backend-issued, per-agent
 * provider — no other change is needed elsewhere.
 */
@Module
@InstallIn(SingletonComponent::class)
object SipModule {

    @Provides
    @Singleton
    fun provideSipCredentialsProvider(): SipCredentialsProvider =
        object : SipCredentialsProvider {
            override suspend fun current(): SipConfig = SipConfig(
                server = BuildConfig.SIP_SERVER,
                user = BuildConfig.SIP_USER,
                password = BuildConfig.SIP_PASSWORD,
            )
        }

    @Provides
    @Singleton
    fun provideLinphoneCoreManager(
        @ApplicationContext context: Context,
        credentialsProvider: SipCredentialsProvider,
    ): LinphoneCoreManager = LinphoneCoreManager(context, credentialsProvider)
}
