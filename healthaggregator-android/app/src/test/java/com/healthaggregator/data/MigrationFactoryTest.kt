package com.healthaggregator.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationFactoryTest {
	private val ctx: Context = ApplicationProvider.getApplicationContext()
	private lateinit var helper: SupportSQLiteOpenHelper
	private lateinit var dbFile: File

	@Before
	fun setUp() {
		dbFile = File.createTempFile("mf_test", ".db").also { it.delete() }
		val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
			.name(dbFile.absolutePath)
			.callback(object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onCreate(db: SupportSQLiteDatabase) {
					db.execSQL("CREATE TABLE dummy (id INTEGER PRIMARY KEY)")
				}
				override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
			})
			.build()
		helper = FrameworkSQLiteOpenHelperFactory().create(config)
	}

	@After
	fun tearDown() {
		helper.close()
		dbFile.delete()
	}

	@Test
	fun `load returns Migration with correct versions`() {
		val factory = MigrationFactory(ctx)
		val migration = factory.load(3, 4)
		assertEquals(3, migration.startVersion)
		assertEquals(4, migration.endVersion)
	}

	@Test
	fun `load executes SQL from asset file`() {
		val factory = MigrationFactory(ctx)
		val migration = factory.load(3, 4)
		migration.migrate(helper.writableDatabase)
		helper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='chat_conversations'").use {
			assertTrue(it.moveToFirst())
		}
	}

	@Test
	fun `load throws IllegalStateException when asset missing`() {
		val factory = MigrationFactory(ctx)
		try {
			factory.load(99, 100)
			fail("Expected IllegalStateException")
		} catch (e: IllegalStateException) {
			assertTrue(e.message!!.contains("99_to_100.sql"))
		}
	}
}
