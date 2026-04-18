package com.healthaggregator.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the Mac API base URL. Nothing sensitive here — no auth token,
 * no PHI. Just the endpoint the bridge pushes to.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object Prefs {
	private val KEY_API_BASE = stringPreferencesKey("api_base_url")

	fun apiBase(context: Context): Flow<String> =
		context.dataStore.data.map { it[KEY_API_BASE] ?: "" }

	suspend fun setApiBase(context: Context, value: String) {
		context.dataStore.edit { it[KEY_API_BASE] = value.trimEnd('/') }
	}
}
