package com.project.vortex.callsagent.di

import com.project.vortex.callsagent.data.repository.AuthRepositoryImpl
import com.project.vortex.callsagent.data.repository.ClientRepositoryImpl
import com.project.vortex.callsagent.data.repository.FollowUpRepositoryImpl
import com.project.vortex.callsagent.data.repository.InteractionRepositoryImpl
import com.project.vortex.callsagent.data.repository.MissedCallRepositoryImpl
import com.project.vortex.callsagent.data.repository.NoteRepositoryImpl
import com.project.vortex.callsagent.domain.repository.AuthRepository
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.MissedCallRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindClientRepository(impl: ClientRepositoryImpl): ClientRepository

    @Binds
    @Singleton
    abstract fun bindInteractionRepository(impl: InteractionRepositoryImpl): InteractionRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindFollowUpRepository(impl: FollowUpRepositoryImpl): FollowUpRepository

    @Binds
    @Singleton
    abstract fun bindMissedCallRepository(impl: MissedCallRepositoryImpl): MissedCallRepository
}
