package com.healthaggregator.sync

import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.MedicalDataSource
import com.healthaggregator.data.entities.SyncJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
	object Idle : SyncState
	data class Syncing(val currentSourceName: String?) : SyncState
	data class Succeeded(val completedAt: Instant, val totals: ImportCounts) : SyncState
	data class Failed(val message: String) : SyncState
}

@Singleton
class SyncManager @Inject constructor(
	private val reader: HealthConnectReader,
	private val importer: FhirImportService,
	private val db: AppDatabase,
) {
	private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
	val state = _state.asStateFlow()

	suspend fun syncAll(): SyncState {
		_state.value = SyncState.Syncing(currentSourceName = null)
		val aggregate = ImportCounts()

		if (!reader.isAvailable()) {
			val failed = SyncState.Failed("Health Connect is not available on this device.")
			_state.value = failed
			return failed
		}
		if (!reader.hasAllPermissions()) {
			val failed = SyncState.Failed("Health Connect permissions not fully granted.")
			_state.value = failed
			return failed
		}

		try {
			val sources = reader.listSources()
			for (source in sources) {
				val mds = resolveOrCreateMapping(source)
				_state.value = SyncState.Syncing(currentSourceName = mds.displayName)
				val jobRow = SyncJob(sourceSystem = mds.sourceSystem, sourceName = mds.displayName, startedAt = Instant.now())
				val jobId = db.syncJobDao().insert(jobRow)
				try {
					val resources = reader.readAllResources(source).map { fhirResourceData(it) }
					val counts = importer.importResources(mds.sourceSystem, mds.displayName, resources, syncJobId = jobId)
					aggregate.sourceRecords += counts.sourceRecords
					aggregate.labs += counts.labs; aggregate.vitals += counts.vitals
					aggregate.conditions += counts.conditions; aggregate.medications += counts.medications
					aggregate.allergies += counts.allergies; aggregate.encounters += counts.encounters
					aggregate.documents += counts.documents; aggregate.patients += counts.patients
					aggregate.reports += counts.reports
					db.syncJobDao().markCompleted(
						id = jobId,
						completedAt = Instant.now(),
						status = "completed",
						sourceRecords = counts.sourceRecords,
						labs = counts.labs, vitals = counts.vitals, conditions = counts.conditions,
						medications = counts.medications, allergies = counts.allergies,
						encounters = counts.encounters, documents = counts.documents,
						patients = counts.patients, reports = counts.reports,
					)
					db.medicalDataSourceDao().touchLastSeen(mds.id, Instant.now(), recordCount = counts.sourceRecords)
				} catch (ex: Exception) {
					db.syncJobDao().markFailed(jobId, Instant.now(), ex.message ?: ex.javaClass.simpleName)
					throw ex
				}
			}
			val ok = SyncState.Succeeded(Instant.now(), aggregate)
			_state.value = ok
			return ok
		} catch (ex: Exception) {
			val failed = SyncState.Failed(ex.message ?: ex.javaClass.simpleName)
			_state.value = failed
			return failed
		}
	}

	private suspend fun resolveOrCreateMapping(source: androidx.health.connect.client.records.MedicalDataSource): MedicalDataSource {
		val dao = db.medicalDataSourceDao()
		dao.findByHealthConnectId(source.id)?.let { return it }
		val display = sourceDisplayName(source)
		val slug = slugify(display, fallback = source.id)
		val row = MedicalDataSource(
			healthConnectSourceId = source.id,
			sourceSystem = slug,
			displayName = display.ifBlank { slug },
			firstSeenAt = Instant.now(),
			lastSeenAt = Instant.now(),
			recordCount = 0,
		)
		val id = dao.insertReturningId(row)
		return row.copy(id = id)
	}
}

/**
 * Extract the FHIR JSON string from a FhirResource. Health Connect 1.1.0-beta02's property
 * name is .data (String) — verified against the compiled class.
 */
private fun fhirResourceData(r: androidx.health.connect.client.records.FhirResource): String = r.data

/**
 * Read the display name from a Health Connect MedicalDataSource. The property is non-nullable
 * String in 1.1.0-beta02 — verified against the compiled class.
 */
private fun sourceDisplayName(source: androidx.health.connect.client.records.MedicalDataSource): String =
	source.displayName
