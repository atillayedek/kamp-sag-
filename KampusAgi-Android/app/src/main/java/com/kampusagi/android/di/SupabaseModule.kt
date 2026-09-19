package com.kampusagi.android.di

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
 * anon/publishable key istemcide bulunması GÜVENLİDİR — gerçek güvenlik
 * sınırı burada değil, PostgreSQL Row Level Security'dedir (bkz.
 * AI_Guidelines.md §3, §45). `service_role` key ve `ANTHROPIC_API_KEY` ise
 * BURADA ASLA bulunmaz; onlar yalnızca Supabase Edge Functions
 * secret'larında tutulur.
 */
private object SupabaseConfig {
    const val PROJECT_URL = "https://ggphcgapgwrcdumfldsc.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_LN4ZITD80GAoIt-dBk379w_BKguB6mR"
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.PROJECT_URL,
        supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Functions)
        install(Realtime)
    }
}
