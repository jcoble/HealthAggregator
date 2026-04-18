package com.healthaggregator.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}
