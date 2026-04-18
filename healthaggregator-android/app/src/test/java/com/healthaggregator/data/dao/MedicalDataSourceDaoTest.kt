package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.MedicalDataSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MedicalDataSourceDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: MedicalDataSourceDao

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.medicalDataSourceDao()
	}

	@After
	fun tearDown() { db.close() }

	@Test
	fun findByHealthConnectId_returns_matching_row() = runTest {
		dao.upsert(sample(healthConnectSourceId = "hc-abc-123", sourceSystem = "cleveland-clinic"))
		val found = dao.findByHealthConnectId("hc-abc-123")
		assertNotNull(found)
		assertEquals("cleveland-clinic", found!!.sourceSystem)
	}

	@Test
	fun findByHealthConnectId_returns_null_when_missing() = runTest {
		val found = dao.findByHealthConnectId("does-not-exist")
		assertNull(found)
	}

	@Test
	fun findBySourceSystem_returns_matching_row() = runTest {
		dao.upsert(sample(healthConnectSourceId = "hc-1", sourceSystem = "summa-health"))
		val found = dao.findBySourceSystem("summa-health")
		assertNotNull(found)
		assertEquals("hc-1", found!!.healthConnectSourceId)
	}

	private fun sample(
		healthConnectSourceId: String = "hc-default",
		sourceSystem: String = "cleveland-clinic",
		displayName: String = "Cleveland Clinic",
	) = MedicalDataSource(
		healthConnectSourceId = healthConnectSourceId,
		sourceSystem = sourceSystem,
		displayName = displayName,
		firstSeenAt = Instant.parse("2026-04-01T00:00:00Z"),
		lastSeenAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
