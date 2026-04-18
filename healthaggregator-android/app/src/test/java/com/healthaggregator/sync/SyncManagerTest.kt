package com.healthaggregator.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.TestFhirFixtures
import com.healthaggregator.data.AppDatabase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncManagerTest {
	private lateinit var db: AppDatabase
	private lateinit var importer: FhirImportService
	private lateinit var reader: HealthConnectReader
	private lateinit var manager: SyncManager

	@Before fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		importer = FhirImportService(db)
		reader = mockk()
		manager = SyncManager(reader, importer, db)
	}

	@After fun tearDown() { db.close() }

	@Test fun sync_creates_MedicalDataSource_mapping_on_first_run() = runTest {
		val source = mockk<androidx.health.connect.client.records.MedicalDataSource>(relaxed = true).apply {
			every { id } returns "hc-src-1"
			every { displayName } returns "Cleveland Clinic"
		}
		every { reader.isAvailable() } returns true
		coEvery { reader.hasAllPermissions() } returns true
		coEvery { reader.listSources() } returns listOf(source)
		coEvery { reader.readAllResources(source) } returns listOf(
			mockk<androidx.health.connect.client.records.FhirResource>(relaxed = true).apply {
				every { data } returns TestFhirFixtures.LAB_HBA1C
			}
		)
		manager.syncAll()
		val mapping = db.medicalDataSourceDao().findByHealthConnectId("hc-src-1")
		assertNotNull(mapping)
		assertEquals("cleveland-clinic", mapping!!.sourceSystem)
	}

	@Test fun sync_writes_SyncJob_audit_row() = runTest {
		val source = mockk<androidx.health.connect.client.records.MedicalDataSource>(relaxed = true).apply {
			every { id } returns "hc-src-2"
			every { displayName } returns "Summa Health"
		}
		every { reader.isAvailable() } returns true
		coEvery { reader.hasAllPermissions() } returns true
		coEvery { reader.listSources() } returns listOf(source)
		coEvery { reader.readAllResources(source) } returns listOf(
			mockk<androidx.health.connect.client.records.FhirResource>(relaxed = true).apply {
				every { data } returns TestFhirFixtures.LAB_HBA1C
			}
		)
		manager.syncAll()
		val jobs = db.syncJobDao().observeRecent(10).first()
		assertEquals(1, jobs.size)
		assertEquals("completed", jobs.first().status)
		assertEquals(1, jobs.first().labObservationsUpserted)
	}

	@Test fun sync_fails_when_permissions_missing() = runTest {
		every { reader.isAvailable() } returns true
		coEvery { reader.hasAllPermissions() } returns false
		val result = manager.syncAll()
		assertTrue(result is SyncState.Failed)
		assertTrue((result as SyncState.Failed).message.contains("permissions"))
	}
}
