package com.oniolla.interviewer.di

import com.oniolla.interviewer.analytics.AppAnalytics
import com.oniolla.interviewer.analytics.FirebaseAppAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun analytics(implementation: FirebaseAppAnalytics): AppAnalytics
}
