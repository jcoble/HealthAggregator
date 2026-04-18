package com.healthaggregator.di

import com.healthaggregator.sync.MigrationLoader
import com.healthaggregator.sync.SyncClient
import com.healthaggregator.sync.SyncCredentials
import com.healthaggregator.sync.SyncRepository
import com.healthaggregator.sync.RowSerializer
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.util.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
	@Provides
	@Singleton
	@Named("sync")
	fun provideSyncHttp(): OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.callTimeout(120, TimeUnit.SECONDS)
		.build()

	@Provides
	@Singleton
	fun provideSyncClient(
		@Named("sync") http: OkHttpClient,
		json: Json,
		secure: SecureStorage,
	): SyncClient =
		SyncClient(http, json) {
			val host = secure.laptopHostname ?: return@SyncClient null
			val token = secure.laptopToken ?: return@SyncClient null
			val baseUrl = if (host.startsWith("http://") || host.startsWith("https://")) host else "http://$host:8719"
			SyncCredentials(baseUrl = baseUrl.trimEnd('/'), token = token)
		}

	@Provides
	@Singleton
	fun provideSyncRepository(
		client: SyncClient,
		db: AppDatabase,
		serializer: RowSerializer,
		migrationLoader: MigrationLoader,
		secure: SecureStorage,
	): SyncRepository = SyncRepository(
		client = client,
		db = db,
		serializer = serializer,
		migrationLoader = migrationLoader,
		phoneSchemaVersion = 5,
		isPairedProvider = { secure.laptopHostname != null && secure.laptopToken != null },
	)
}
