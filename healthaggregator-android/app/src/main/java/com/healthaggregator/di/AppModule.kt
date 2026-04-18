package com.healthaggregator.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Module for app-level singletons. HealthConnectClient provider lands here in Phase 3 Task 13.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
	// Phase 3: HealthConnectClient provider, SyncManager provider, etc.
}
