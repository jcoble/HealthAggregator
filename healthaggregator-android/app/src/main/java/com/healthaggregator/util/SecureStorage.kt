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

	var laptopHostname: String?
		get() = prefs.getString(KEY_LAPTOP_HOSTNAME, null)?.ifEmpty { null }
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_LAPTOP_HOSTNAME) else putString(KEY_LAPTOP_HOSTNAME, value); apply() }

	var laptopToken: String?
		get() = prefs.getString(KEY_LAPTOP_TOKEN, null)?.ifEmpty { null }
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_LAPTOP_TOKEN) else putString(KEY_LAPTOP_TOKEN, value); apply() }

	var lastSyncAt: Long
		get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
		set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

	var lastSyncSummary: String?
		get() = prefs.getString(KEY_LAST_SYNC_SUMMARY, null)
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_LAST_SYNC_SUMMARY) else putString(KEY_LAST_SYNC_SUMMARY, value); apply() }

	fun clearApiKey() { openAiApiKey = null }

	fun clearLaptopPairing() {
		laptopHostname = null
		laptopToken = null
		lastSyncAt = 0L
		lastSyncSummary = null
	}

	companion object {
		private const val FILE_NAME = "healthaggregator_secure"
		private const val KEY_OPENAI_API_KEY = "openai_api_key"
		private const val KEY_DATA_SHARING = "data_sharing_enabled"
		private const val KEY_SELECTED_MODEL = "selected_model"
		private const val KEY_DISCLAIMER = "disclaimer_acknowledged"
		private const val KEY_LAPTOP_HOSTNAME = "laptop_hostname"
		private const val KEY_LAPTOP_TOKEN = "laptop_token"
		private const val KEY_LAST_SYNC_AT = "last_sync_at"
		private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
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
