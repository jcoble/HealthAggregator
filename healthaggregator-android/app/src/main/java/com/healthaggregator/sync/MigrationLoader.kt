package com.healthaggregator.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationLoader @Inject constructor(@ApplicationContext private val ctx: Context) {
	fun load(from: Int, to: Int): String {
		val path = "migrations/${from}_to_${to}.sql"
		return try {
			ctx.assets.open(path).bufferedReader().use { it.readText() }
		} catch (e: FileNotFoundException) {
			throw IllegalStateException("Migration asset missing on phone: $path", e)
		}
	}
}
