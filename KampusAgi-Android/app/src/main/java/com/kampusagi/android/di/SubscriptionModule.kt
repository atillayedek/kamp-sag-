package com.kampusagi.android.di

import com.kampusagi.android.data.subscription.BillingGateway
import com.kampusagi.android.data.subscription.PlayBillingGateway
import com.kampusagi.android.data.subscription.SupabaseSubscriptionRepository
import com.kampusagi.android.domain.subscription.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionModule {

    @Binds
    @Singleton
    abstract fun bindBillingGateway(impl: PlayBillingGateway): BillingGateway

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SupabaseSubscriptionRepository): SubscriptionRepository
}
