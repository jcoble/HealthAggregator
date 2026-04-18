package com.healthaggregator.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.FhirResource
import androidx.health.connect.client.records.MedicalDataSource
import androidx.health.connect.client.records.MedicalResource
import androidx.health.connect.client.request.GetMedicalDataSourcesRequest
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectReader @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val resourceTypes = listOf(
		MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS,
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

	// One permission string per medical resource type category.
	val readPermissions: Set<String> = setOf(
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_PERSONAL_DETAILS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_PRACTITIONER_DETAILS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_LABORATORY_RESULTS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_CONDITIONS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_MEDICATIONS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_VISITS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_VITAL_SIGNS,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_PROCEDURES,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_SOCIAL_HISTORY,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_VACCINES,
		HealthPermission.PERMISSION_READ_MEDICAL_DATA_PREGNANCY,
	)

	private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

	fun isAvailable(): Boolean =
		HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

	suspend fun grantedPermissions(): Set<String> =
		client.permissionController.getGrantedPermissions()

	suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(readPermissions)

	suspend fun listSources(): List<MedicalDataSource> =
		client.getMedicalDataSources(GetMedicalDataSourcesRequest(packageNames = emptyList()))

	suspend fun readAllResources(source: MedicalDataSource): List<FhirResource> {
		val out = mutableListOf<FhirResource>()
		for (type in resourceTypes) {
			val initial = ReadMedicalResourcesInitialRequest(
				medicalResourceType = type,
				medicalDataSourceIds = setOf(source.id),
				pageSize = 500,
			)
			var response = client.readMedicalResources(initial)
			response.medicalResources.forEach { out += it.fhirResource }
			while (response.nextPageToken != null) {
				response = client.readMedicalResources(
					ReadMedicalResourcesPageRequest(
						pageToken = response.nextPageToken!!,
						pageSize = 500,
					)
				)
				response.medicalResources.forEach { out += it.fhirResource }
			}
		}
		return out
	}
}
