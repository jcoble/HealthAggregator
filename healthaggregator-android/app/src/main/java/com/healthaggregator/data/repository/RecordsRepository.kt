package com.healthaggregator.data.repository

import com.healthaggregator.data.LabNameNormalizer
import com.healthaggregator.data.LabPanelAggregate
import com.healthaggregator.data.dao.AllergyDao
import com.healthaggregator.data.dao.ConditionDao
import com.healthaggregator.data.dao.DocumentDao
import com.healthaggregator.data.dao.EncounterDao
import com.healthaggregator.data.dao.LabDao
import com.healthaggregator.data.dao.MedicalDataSourceDao
import com.healthaggregator.data.dao.MedicationDao
import com.healthaggregator.data.dao.SourceRecordDao
import com.healthaggregator.data.dao.SyncJobDao
import com.healthaggregator.data.dao.VitalsDao
import com.healthaggregator.data.entities.AllergyRecord
import com.healthaggregator.data.entities.ConditionRecord
import com.healthaggregator.data.entities.DocumentRecord
import com.healthaggregator.data.entities.EncounterRecord
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.MedicalDataSource
import com.healthaggregator.data.entities.MedicationRecord
import com.healthaggregator.data.entities.SyncJob
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordsRepository @Inject constructor(
	private val labs: LabDao,
	private val vitals: VitalsDao,
	private val medications: MedicationDao,
	private val conditions: ConditionDao,
	private val allergies: AllergyDao,
	private val encounters: EncounterDao,
	private val documents: DocumentDao,
	private val sources: MedicalDataSourceDao,
	private val syncJobs: SyncJobDao,
	private val sourceRecords: SourceRecordDao,
) {
	fun observeLabs(): Flow<List<LabObservation>> = labs.observeAll()
	fun observeVitals(): Flow<List<VitalsObservation>> = vitals.observeAll()
	fun observeMedications(): Flow<List<MedicationRecord>> = medications.observeAll()
	fun observeConditions(): Flow<List<ConditionRecord>> = conditions.observeAll()
	fun observeAllergies(): Flow<List<AllergyRecord>> = allergies.observeAll()
	fun observeEncounters(): Flow<List<EncounterRecord>> = encounters.observeAll()
	fun observeDocuments(): Flow<List<DocumentRecord>> = documents.observeAll()
	fun observeSources(): Flow<List<MedicalDataSource>> = sources.observeAll()

	fun countLabs(): Flow<Int> = labs.countAll()
	fun countVitals(): Flow<Int> = vitals.countAll()
	fun countMedications(): Flow<Int> = medications.countAll()
	fun countConditions(): Flow<Int> = conditions.countAll()
	fun countAllergies(): Flow<Int> = allergies.countAll()
	fun countEncounters(): Flow<Int> = encounters.countAll()
	fun countDocuments(): Flow<Int> = documents.countAll()

	fun observeLatestSync(): Flow<SyncJob?> = syncJobs.observeLatest()

	fun observePanels(): Flow<List<LabPanelAggregate>> = labs.observePanels()
	fun observeLabsByServiceRequest(sr: String): Flow<List<LabObservation>> = labs.observeByServiceRequest(sr)
	fun observeLabsByLoinc(loinc: String): Flow<List<LabObservation>> = labs.observeByLoinc(loinc)
	fun observeLabsByLoincOrCanonical(loinc: String?, canonical: String?): Flow<List<LabObservation>> =
		labs.observeByLoincOrCanonical(loinc, canonical)
	suspend fun findRawJson(source: String, fhirReference: String): String? = sourceRecords.findRawJson(source, fhirReference)

	suspend fun ensureNamesNormalized() {
		val needsBackfill = labs.rowsNeedingCanonicalBackfill()
		for (row in needsBackfill) {
			val canonicalPanel = LabNameNormalizer.normalize(row.serviceRequestDisplay)
			val canonicalTest = LabNameNormalizer.normalize(row.testName)
			labs.setCanonicals(row.id, canonicalPanel, canonicalTest)
		}
	}
}
