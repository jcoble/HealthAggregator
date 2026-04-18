package com.healthaggregator.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(@ApplicationContext ctx: Context) {

	private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
		ctx,
		FILE_NAME,
		MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
		EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
		EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
	)

	var openAiApiKey: String?
		get() = prefs.getString(KEY_OPENAI_API_KEY, null)?.ifEmpty { null }
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_OPENAI_API_KEY) else putString(KEY_OPENAI_API_KEY, value); apply() }

	var dataSharingEnabled: Boolean
		get() = prefs.getBoolean(KEY_DATA_SHARING, false)
		set(value) = prefs.edit().putBoolean(KEY_DATA_SHARING, value).apply()

	var selectedModel: String
		get() = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
		set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()

	var disclaimerAcknowledged: Boolean
		get() = prefs.getBoolean(KEY_DISCLAIMER, false)
		set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()

	fun clearApiKey() { openAiApiKey = null }

	companion object {
		private const val FILE_NAME = "healthaggregator_secure"
		private const val KEY_OPENAI_API_KEY = "openai_api_key"
		private const val KEY_DATA_SHARING = "data_sharing_enabled"
		private const val KEY_SELECTED_MODEL = "selected_model"
		private const val KEY_DISCLAIMER = "disclaimer_acknowledged"
		const val DEFAULT_MODEL = "gpt-5"

		val ELIGIBLE_FREE_TIER_MODELS = setOf("gpt-5", "gpt-5-mini", "gpt-5-nano")
		val AVAILABLE_MODELS = listOf(
			"gpt-5" to "GPT-5 (free-tier eligible)",
			"gpt-5.4" to "GPT-5.4 (latest, paid)",
			"gpt-5.4-pro" to "GPT-5.4 Pro (best quality, paid)",
			"gpt-5.4-mini" to "GPT-5.4 Mini (fast, cheap)",
			"gpt-5.4-nano" to "GPT-5.4 Nano (cheapest)",
			"gpt-5-mini" to "GPT-5 Mini",
			"gpt-5-nano" to "GPT-5 Nano",
		)
	}
}
