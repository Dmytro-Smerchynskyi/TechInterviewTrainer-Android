package com.oniolla.interviewer.di

import com.oniolla.interviewer.data.billing.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds abstract fun billingStore(store: PlayBillingStore): BillingStore
    @Binds abstract fun pendingPurchases(store: EncryptedPendingPurchaseStore): PendingPurchaseStore
}
