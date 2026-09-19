package com.kampusagi.android.di

import com.kampusagi.android.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/**
 * URL ve publishable (anon) anahtar `gradle.properties` -> `BuildConfig` üzerinden gelir.
 * Publishable anahtar istemcide bulunması GÜVENLİDİR — gerçek güvenlik sınırı PostgreSQL Row Level
 * Security'dedir (bkz. AI_Guidelines.md §3, §45). `service_role` anahtarı ve `ANTHROPIC_API_KEY`
 * BURADA ASLA bulunmaz; onlar yalnızca Supabase Edge Function secret'larındadır.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Functions)
        install(Realtime)
    }
}
