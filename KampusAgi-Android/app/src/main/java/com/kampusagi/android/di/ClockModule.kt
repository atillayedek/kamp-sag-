package com.kampusagi.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Zaman kaynağı enjekte edilir; testlerde sabit/ilerletilebilir saat kullanılır. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}
