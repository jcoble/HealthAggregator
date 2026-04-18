package com.healthaggregator.bridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.FhirResource
import androidx.health.connect.client.records.MedicalDataSource
import androidx.health.connect.client.records.MedicalResource
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest

/**
 * Reads clinical FHIR records from Health Connect (the Personal Health Record API)
 * and groups them by MedicalDataSource so each group can be uploaded with the
 * correct source-system tag to the HealthAggregator API.
 *
 * CommonHealth writes records to Health Connect keyed by MedicalDataSource — one
 * source per connected provider (e.g., Cleveland Clinic, Summa Health). The bridge
 * enumerates those sources, reads every FHIR resource per source, and returns
 * the data keyed for upload.
 */
class HealthConnectReader(private val context: Context) {

	/** All permissions this app reads. Granted by the user through Health Connect's system dialog. */
	val readPermissions: Set<String> = setOf(
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_PATIENT_DEMOGRAPHICS),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS),
		HealthPermission.getReadPermission(MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS),
	)

	val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

	/**
	 * True when Health Connect is installed and usable on this device.
	 * (It's preinstalled on Android 14+, but the service can be disabled.)
	 */
	fun isAvailable(): Boolean {
		val status = HealthConnectClient.getSdkStatus(context)
		return status == HealthConnectClient.SDK_AVAILABLE
	}

	suspend fun grantedPermissions(): Set<String> =
		client.permissionController.getGrantedPermissions()

	suspend fun hasAllPermissions(): Boolean {
		val granted = grantedPermissions()
		return readPermissions.all { it in granted }
	}

	/**
	 * Returns the list of data sources (one per connected provider — e.g. Cleveland Clinic, Summa Health).
	 */
	suspend fun listSources(): List<MedicalDataSource> {
		val request = androidx.health.connect.client.request.GetMedicalDataSourcesRequest(packageNames = emptyList())
		return client.getMedicalDataSources(request)
	}

	/** Resource types we collect, keyed to stable lowercase slugs for upload. */
	private val resourceTypes = listOf(
		MedicalResource.MEDICAL_RESOURCE_TYPE_PATIENT_DEMOGRAPHICS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
		MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES,
		MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY,
		MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES,
		MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY,
	)

	/**
	 * For a given source, read every FHIR resource across every relevant type,
	 * paging to exhaustion. Returns the raw FHIR JSON strings.
	 */
	suspend fun readAllResources(source: MedicalDataSource): List<FhirResource> {
		val out = mutableListOf<FhirResource>()
		for (type in resourceTypes) {
			val initial = ReadMedicalResourcesInitialRequest(
				medicalResourceType = type,
				dataSourceIds = setOf(source.id),
				pageSize = 500
			)
			var response = client.readMedicalResources(initial)
			response.medicalResources.forEach { out += it.fhirResource }
			while (response.nextPageToken != null) {
				response = client.readMedicalResources(
					androidx.health.connect.client.request.ReadMedicalResourcesPageRequest(
						pageToken = response.nextPageToken!!,
						pageSize = 500
					)
				)
				response.medicalResources.forEach { out += it.fhirResource }
			}
		}
		return out
	}

	/**
	 * Builds a FHIR R4 Bundle (searchset) wrapping the given FhirResources.
	 * HealthAggregator's FhirImportService walks bundle entries and upserts per resourceType.
	 */
	fun buildBundle(resources: List<FhirResource>): String {
		val entries = resources.joinToString(",") { fhir ->
			// fhir.data is the FHIR JSON of a single resource as published by the source system
			"""{"resource":${fhir.data}}"""
		}
		return """{"resourceType":"Bundle","type":"searchset","entry":[$entries]}"""
	}
}
