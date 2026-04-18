package com.healthaggregator.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.FileNotFoundException

/**
 * Loads migration SQL from `assets/migrations/{from}_to_{to}.sql` and produces Room Migration objects.
 *
 * The SQL files are the single source of truth for schema changes, shared between the Android app
 * (this class) and the laptop sync daemon (which reads the same files from the repo, or receives
 * them over HTTP during a sync).
 *
 * Missing assets are a programmer error: a Migration is wired in AppDatabaseMigrations.kt that
 * references an asset the engineer forgot to create. Fail fast.
 */
@Singleton
class MigrationFactory @Inject constructor(@ApplicationContext private val ctx: Context) {

	fun load(from: Int, to: Int): Migration {
		val assetPath = "migrations/${from}_to_${to}.sql"
		val sql = try {
			ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
		} catch (e: FileNotFoundException) {
			throw IllegalStateException("Migration asset missing: $assetPath", e)
		}
		return object : Migration(from, to) {
			override fun migrate(db: SupportSQLiteDatabase) {
				splitStatements(sql).forEach { db.execSQL(it) }
			}
		}
	}

	internal fun splitStatements(sql: String): List<String> =
		sql.split(';').map { it.trim() }.filter { it.isNotEmpty() }
}
