package com.healthaggregator.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
	private val dbName = "migration-test.db"

	@get:Rule
	val helper = MigrationTestHelper(
		InstrumentationRegistry.getInstrumentation(),
		AppDatabase::class.java,
		emptyList(),
		FrameworkSQLiteOpenHelperFactory(),
	)

	@Test
	fun migrate_1_to_2_preserves_existing_labs_and_adds_columns() {
		// Seed v1: one row without the new columns
		helper.createDatabase(dbName, 1).use { db ->
			db.execSQL("""
				INSERT INTO lab_observations(
					sourceSystem, sourceName, fhirReference, resourceId, testName, status, importedAt
				) VALUES(
					'cleveland-clinic', 'Cleveland Clinic', 'Observation/x', 'x', 'HbA1c', 'final', 0
				)
			""".trimIndent())
		}

		// Run migration and open as v2
		val db2 = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

		// Confirm row preserved + new columns readable as null
		val cursor = db2.query("SELECT testName, serviceRequestReference, serviceRequestDisplay FROM lab_observations")
		cursor.use {
			assertEquals(1, it.count)
			assertEquals(true, it.moveToFirst())
			assertEquals("HbA1c", it.getString(0))
			assertEquals(true, it.isNull(1))
			assertEquals(true, it.isNull(2))
		}

		// Confirm new index exists (sqlite_master)
		val idxCursor = db2.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='lab_observations'")
		val indexNames = buildList {
			while (idxCursor.moveToNext()) add(idxCursor.getString(0))
		}
		idxCursor.close()
		assertEquals(true, indexNames.contains("index_lab_observations_serviceRequestReference"))
	}

	@Test
	fun migrate_2_to_3_adds_canonical_columns_and_preserves_rows() {
		// Seed v2: insert a row with serviceRequestDisplay + testName set, no canonicals yet
		helper.createDatabase(dbName, 2).use { db ->
			db.execSQL("""
				INSERT INTO lab_observations(
					sourceSystem, sourceName, fhirReference, resourceId, testName, status, importedAt,
					serviceRequestReference, serviceRequestDisplay
				) VALUES(
					'cleveland-clinic', 'Cleveland Clinic', 'Observation/y', 'y', 'GLUCOSE', 'final', 0,
					'ServiceRequest/bmp-1', 'COMPREHENSIVE METABOLIC PANEL'
				)
			""".trimIndent())
		}

		val db3 = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

		val cursor = db3.query("SELECT testName, serviceRequestDisplay, canonicalPanelName, canonicalTestName FROM lab_observations")
		cursor.use {
			assertEquals(1, it.count)
			assertEquals(true, it.moveToFirst())
			assertEquals("GLUCOSE", it.getString(0))
			assertEquals("COMPREHENSIVE METABOLIC PANEL", it.getString(1))
			assertEquals(true, it.isNull(2)) // not backfilled by the migration itself
			assertEquals(true, it.isNull(3))
		}

		val idxCursor = db3.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='lab_observations'")
		val indexNames = buildList {
			while (idxCursor.moveToNext()) add(idxCursor.getString(0))
		}
		idxCursor.close()
		assertEquals(true, indexNames.contains("index_lab_observations_canonicalPanelName"))
		assertEquals(true, indexNames.contains("index_lab_observations_canonicalTestName"))
	}

	@Test
	fun migrate_3_to_4() {
		// Create v3 DB with a lab row to prove existing data isn't lost.
		helper.createDatabase(dbName, 3).apply {
			execSQL(
				"""
				INSERT INTO lab_observations
				(sourceSystem, sourceName, fhirReference, resourceId, testName, status, importedAt)
				VALUES ('cleveland-clinic', 'Cleveland Clinic', 'Observation/abc', 'abc', 'Hemoglobin A1c', '', 1700000000000)
				""".trimIndent()
			)
			close()
		}

		val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

		// Existing row survived
		db.query("SELECT COUNT(*) FROM lab_observations").use { c ->
			c.moveToFirst(); assertEquals(1, c.getInt(0))
		}

		// New tables exist and are empty
		db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
			c.moveToFirst(); assertEquals(0, c.getInt(0))
		}
		db.query("SELECT COUNT(*) FROM chat_messages").use { c ->
			c.moveToFirst(); assertEquals(0, c.getInt(0))
		}

		// Insert + FK cascade smoke
		db.execSQL(
			"""
			INSERT INTO chat_conversations (id, title, createdAt, updatedAt, modelId)
			VALUES ('c1', 'Test', 1700000000000, 1700000000000, 'gpt-5')
			""".trimIndent()
		)
		db.execSQL(
			"""
			INSERT INTO chat_messages (id, conversationId, role, content, createdAt)
			VALUES ('m1', 'c1', 'user', 'hi', 1700000000000)
			""".trimIndent()
		)

		db.query("SELECT COUNT(*) FROM chat_messages").use { c ->
			c.moveToFirst(); assertEquals(1, c.getInt(0))
		}

		// Index check
		db.query(
			"SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='chat_messages'"
		).use { c ->
			val names = mutableListOf<String>()
			while (c.moveToNext()) names.add(c.getString(0))
			assertTrue(names.any { it == "index_chat_messages_conversationId_createdAt" })
		}
	}
}
