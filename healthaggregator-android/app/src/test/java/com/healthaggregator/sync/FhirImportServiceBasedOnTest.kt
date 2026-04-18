package com.healthaggregator.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.TestFhirFixtures
import com.healthaggregator.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FhirImportServiceBasedOnTest {
	private lateinit var db: AppDatabase
	private lateinit var service: FhirImportService

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		service = FhirImportService(db)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun lab_with_basedOn_populates_serviceRequest_fields() = runTest {
		service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_BMP_GLUCOSE))
		val lab = db.labDao().observeAll().first().first()
		assertEquals("ServiceRequest/bmp-order-1", lab.serviceRequestReference)
		assertEquals("Basic Metabolic Panel", lab.serviceRequestDisplay)
	}

	@Test
	fun lab_without_basedOn_leaves_serviceRequest_fields_null() = runTest {
		service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_HBA1C))
		val lab = db.labDao().observeAll().first().first()
		assertEquals(null, lab.serviceRequestReference)
		assertEquals(null, lab.serviceRequestDisplay)
	}
}
