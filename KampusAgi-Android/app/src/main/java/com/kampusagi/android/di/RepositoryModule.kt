package com.kampusagi.android.di

import com.kampusagi.android.data.auth.SupabaseAuthRepository
import com.kampusagi.android.data.community.SupabasePostRepository
import com.kampusagi.android.data.profile.SupabaseProfileRepository
import com.kampusagi.android.data.university.SupabaseUniversityRepository
import com.kampusagi.android.data.verification.SupabaseStudentVerificationRepository
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.community.PostRepository
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.university.UniversityRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
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
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindStudentVerificationRepository(impl: SupabaseStudentVerificationRepository): StudentVerificationRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: SupabaseProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindUniversityRepository(impl: SupabaseUniversityRepository): UniversityRepository

    @Binds
    @Singleton
    abstract fun bindPostRepository(impl: SupabasePostRepository): PostRepository
}
