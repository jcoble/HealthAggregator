package com.healthaggregator.di

import com.healthaggregator.ai.LlmClient
import com.healthaggregator.ai.OpenAiClient
import com.healthaggregator.util.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
	@Provides
	@Singleton
	fun provideJson(): Json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = false
		prettyPrint = false
	}

	@Provides
	@Singleton
	fun provideLlmClient(secure: SecureStorage, json: Json): LlmClient =
		OpenAiClient(apiKeyProvider = { secure.openAiApiKey }, json = json)
}
