package com.kampusagi.android.di

import com.kampusagi.android.data.account.SupabaseAccountRepository
import com.kampusagi.android.data.analytics.SupabaseAnalyticsTracker
import com.kampusagi.android.data.auth.SupabaseAuthRepository
import com.kampusagi.android.data.chat.SupabaseChatRepository
import com.kampusagi.android.data.community.SupabasePostRepository
import com.kampusagi.android.data.events.SupabaseEventsRepository
import com.kampusagi.android.data.match.SupabaseMatchRepository
import com.kampusagi.android.data.profile.SupabaseProfileRepository
import com.kampusagi.android.data.requirement.SupabaseRequirementRepository
import com.kampusagi.android.data.settings.DataStorePrivacyPreferenceRepository
import com.kampusagi.android.data.settings.DataStoreThemePreferenceRepository
import com.kampusagi.android.data.settings.SupabaseNotificationPreferencesRepository
import com.kampusagi.android.data.university.SupabaseUniversityRepository
import com.kampusagi.android.data.verification.SupabaseStudentVerificationRepository
import com.kampusagi.android.domain.account.AccountRepository
import com.kampusagi.android.domain.analytics.AnalyticsTracker
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.community.PostRepository
import com.kampusagi.android.domain.events.EventsRepository
import com.kampusagi.android.domain.match.MatchRepository
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.requirement.RequirementRepository
import com.kampusagi.android.domain.settings.NotificationPreferencesRepository
import com.kampusagi.android.domain.settings.PrivacyPreferenceRepository
import com.kampusagi.android.domain.settings.ThemePreferenceRepository
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

    @Binds
    @Singleton
    abstract fun bindRequirementRepository(impl: SupabaseRequirementRepository): RequirementRepository

    @Binds
    @Singleton
    abstract fun bindMatchRepository(impl: SupabaseMatchRepository): MatchRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: SupabaseChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: SupabaseAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindNotificationPreferencesRepository(impl: SupabaseNotificationPreferencesRepository): NotificationPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindThemePreferenceRepository(impl: DataStoreThemePreferenceRepository): ThemePreferenceRepository

    @Binds
    @Singleton
    abstract fun bindEventsRepository(impl: SupabaseEventsRepository): EventsRepository

    @Binds
    @Singleton
    abstract fun bindPrivacyPreferenceRepository(impl: DataStorePrivacyPreferenceRepository): PrivacyPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsTracker(impl: SupabaseAnalyticsTracker): AnalyticsTracker
}
