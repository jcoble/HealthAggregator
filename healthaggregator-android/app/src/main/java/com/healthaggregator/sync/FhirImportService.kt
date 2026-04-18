package com.healthaggregator.sync

import androidx.room.withTransaction
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.LabNameNormalizer
import com.healthaggregator.data.entities.*
import kotlinx.serialization.json.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ImportCounts(
	var sourceRecords: Int = 0,
	var labs: Int = 0,
	var vitals: Int = 0,
	var conditions: Int = 0,
	var medications: Int = 0,
	var allergies: Int = 0,
	var encounters: Int = 0,
	var documents: Int = 0,
	var patients: Int = 0,
	var reports: Int = 0,
)

@Singleton
class FhirImportService @Inject constructor(
	private val db: AppDatabase,
) {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }

	/**
	 * Imports a list of FHIR JSON resources (as raw strings) under a single (sourceSystem, sourceName),
	 * within one Room withTransaction block. Returns counts per typed table.
	 */
	suspend fun importResources(
		sourceSystem: String,
		sourceName: String,
		resources: List<String>,
		syncJobId: Long? = null,
	): ImportCounts {
		val counts = ImportCounts()
		val now = Instant.now()

		db.withTransaction {
			for (rawJson in resources) {
				val root = try { json.parseToJsonElement(rawJson).jsonObject } catch (e: Exception) { continue }
				val resourceType = root["resourceType"]?.jsonPrimitive?.contentOrNull ?: continue
				val resourceId = root["id"]?.jsonPrimitive?.contentOrNull ?: continue
				val fhirReference = "$resourceType/$resourceId"

				// 1. Always upsert SourceRecord as raw backup
				db.sourceRecordDao().upsert(SourceRecord(
					syncJobId = syncJobId,
					sourceSystem = sourceSystem,
					sourceName = sourceName,
					resourceType = resourceType,
					resourceId = resourceId,
					fhirReference = fhirReference,
					rawJson = rawJson,
					importedAt = now,
				))
				counts.sourceRecords++

				// 2. Dispatch on resourceType
				when (resourceType) {
					"Patient" -> {
						db.patientDao().upsert(PatientRecord(
							sourceSystem = sourceSystem,
							fhirId = resourceId,
							displayName = readHumanName(root),
							birthDate = root["birthDate"]?.jsonPrimitive?.contentOrNull,
							updatedAt = now,
						))
						counts.patients++
					}
					"Observation" -> {
						val category = firstCategoryCode(root)
						when (category) {
							"laboratory" -> {
								db.labDao().upsert(buildLab(sourceSystem, sourceName, root, resourceId, fhirReference, now))
								counts.labs++
							}
							"vital-signs" -> {
								val vitalRows = buildVitals(sourceSystem, sourceName, root, resourceId, fhirReference, now)
								db.vitalsDao().upsertAll(vitalRows)
								counts.vitals += vitalRows.size
							}
							else -> { /* SourceRecord already stored */ }
						}
					}
					"DiagnosticReport" -> {
						db.diagnosticReportDao().upsert(buildDiagnosticReport(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.reports++
					}
					"Condition" -> {
						db.conditionDao().upsert(buildCondition(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.conditions++
					}
					"MedicationRequest", "MedicationStatement", "Medication" -> {
						db.medicationDao().upsert(buildMedication(sourceSystem, sourceName, root, resourceType, resourceId, fhirReference, now))
						counts.medications++
					}
					"AllergyIntolerance" -> {
						db.allergyDao().upsert(buildAllergy(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.allergies++
					}
					"Encounter" -> {
						db.encounterDao().upsert(buildEncounter(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.encounters++
					}
					"DocumentReference" -> {
						db.documentDao().upsert(buildDocument(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.documents++
					}
					else -> { /* unknown resourceType — SourceRecord-only */ }
				}
			}
		}
		return counts
	}

	// --- Helpers ---

	private fun firstCategoryCode(root: JsonObject): String? =
		root["category"]?.jsonArray?.firstOrNull()
			?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull

	private fun readHumanName(root: JsonObject): String? {
		val names = root["name"]?.jsonArray ?: return null
		val name = names.firstOrNull()?.jsonObject ?: return null
		val given = name["given"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content } ?: ""
		val family = name["family"]?.jsonPrimitive?.contentOrNull ?: ""
		return listOf(given, family).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
	}

	private fun parseInstant(s: String?): Instant? = s?.let {
		try { Instant.parse(it) } catch (e: Exception) {
			try { Instant.parse("${it}T00:00:00Z") } catch (e2: Exception) { null }
		}
	}

	private fun buildLab(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant): LabObservation {
		val code = root["code"]?.jsonObject
		val loinc = code?.get("coding")?.jsonArray
			?.firstOrNull { it.jsonObject["system"]?.jsonPrimitive?.contentOrNull?.contains("loinc") == true }
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
		val testName = code?.get("text")?.jsonPrimitive?.contentOrNull
			?: code?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull
			?: "Unknown lab"
		val value = root["valueQuantity"]?.jsonObject
		val numeric = value?.get("value")?.jsonPrimitive?.doubleOrNull
		val unit = value?.get("unit")?.jsonPrimitive?.contentOrNull
		val interpretation = root["interpretation"]?.jsonArray?.firstOrNull()
			?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
		val range = root["referenceRange"]?.jsonArray?.firstOrNull()?.jsonObject
		val refLow = range?.get("low")?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
		val refHigh = range?.get("high")?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
		val effective = parseInstant(root["effectiveDateTime"]?.jsonPrimitive?.contentOrNull)
			?: parseInstant(root["effectiveInstant"]?.jsonPrimitive?.contentOrNull)
		val basedOn = root["basedOn"]?.jsonArray?.firstOrNull()?.jsonObject
		val serviceRequestReference = basedOn?.get("reference")?.jsonPrimitive?.contentOrNull
		val serviceRequestDisplay = basedOn?.get("display")?.jsonPrimitive?.contentOrNull
		val canonicalPanelName = LabNameNormalizer.normalize(serviceRequestDisplay)
		val canonicalTestName = LabNameNormalizer.normalize(testName)
		return LabObservation(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			diagnosticReportReference = null,
			loincCode = loinc, testName = testName,
			numericValue = numeric, textValue = root["valueString"]?.jsonPrimitive?.contentOrNull,
			unit = unit, referenceLow = refLow, referenceHigh = refHigh,
			referenceText = range?.get("text")?.jsonPrimitive?.contentOrNull,
			interpretation = interpretation,
			effectiveAt = effective,
			status = root["status"]?.jsonPrimitive?.contentOrNull ?: "",
			importedAt = now,
			serviceRequestReference = serviceRequestReference,
			serviceRequestDisplay = serviceRequestDisplay,
			canonicalPanelName = canonicalPanelName,
			canonicalTestName = canonicalTestName,
		)
	}

	private fun buildVitals(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant): List<VitalsObservation> {
		val code = root["code"]?.jsonObject
		val parentLoinc = code?.get("coding")?.jsonArray
			?.firstOrNull { it.jsonObject["system"]?.jsonPrimitive?.contentOrNull?.contains("loinc") == true }
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
		val parentName = code?.get("text")?.jsonPrimitive?.contentOrNull ?: "Vital sign"
		val effective = parseInstant(root["effectiveDateTime"]?.jsonPrimitive?.contentOrNull)
		val patient = subjectRef(root)
		val components = root["component"]?.jsonArray
		if (components == null) {
			val value = root["valueQuantity"]?.jsonObject
			return listOf(VitalsObservation(
				sourceSystem = sourceSystem, sourceName = sourceName,
				fhirReference = fhirRef, resourceId = resourceId,
				patientFhirId = patient,
				loincCode = parentLoinc, code = parentLoinc ?: parentName,
				displayName = parentName,
				numericValue = value?.get("value")?.jsonPrimitive?.doubleOrNull,
				unit = value?.get("unit")?.jsonPrimitive?.contentOrNull,
				componentCode = "", effectiveAt = effective, importedAt = now,
			))
		}
		return components.map { compRaw ->
			val comp = compRaw.jsonObject
			val compCode = comp["code"]?.jsonObject
			val compLoinc = compCode?.get("coding")?.jsonArray
				?.firstOrNull { it.jsonObject["system"]?.jsonPrimitive?.contentOrNull?.contains("loinc") == true }
				?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
			val compName = compCode?.get("text")?.jsonPrimitive?.contentOrNull
				?: compCode?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull
				?: "Component"
			val compValue = comp["valueQuantity"]?.jsonObject
			VitalsObservation(
				sourceSystem = sourceSystem, sourceName = sourceName,
				fhirReference = fhirRef, resourceId = resourceId,
				patientFhirId = patient,
				loincCode = compLoinc, code = compLoinc ?: compName,
				displayName = compName,
				numericValue = compValue?.get("value")?.jsonPrimitive?.doubleOrNull,
				unit = compValue?.get("unit")?.jsonPrimitive?.contentOrNull,
				componentCode = compLoinc ?: compName,
				effectiveAt = effective,
				importedAt = now,
			)
		}
	}

	private fun buildDiagnosticReport(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		DiagnosticReportRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			codeText = root["code"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			issuedAt = parseInstant(root["issued"]?.jsonPrimitive?.contentOrNull),
			resultReferences = null,
			importedAt = now,
		)

	private fun buildCondition(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		ConditionRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			codeText = root["code"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
				?: root["code"]?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull,
			clinicalStatus = root["clinicalStatus"]?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull,
			onsetAt = parseInstant(root["onsetDateTime"]?.jsonPrimitive?.contentOrNull),
			recordedAt = parseInstant(root["recordedDate"]?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)

	private fun buildMedication(sourceSystem: String, sourceName: String, root: JsonObject, resourceType: String, resourceId: String, fhirRef: String, now: Instant): MedicationRecord {
		val medicationText = when (resourceType) {
			"Medication" -> codeTextOrDisplay(root["code"]?.jsonObject)
			else -> codeTextOrDisplay(root["medicationCodeableConcept"]?.jsonObject)
				?: root["medicationReference"]?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull
				?: codeTextOrDisplay(root["code"]?.jsonObject)
		}
		return MedicationRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			medicationText = medicationText,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			authoredAt = parseInstant(root["authoredOn"]?.jsonPrimitive?.contentOrNull)
				?: parseInstant(root["dateAsserted"]?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)
	}

	private fun codeTextOrDisplay(code: JsonObject?): String? {
		if (code == null) return null
		return code["text"]?.jsonPrimitive?.contentOrNull
			?: code["coding"]?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull
	}

	private fun buildAllergy(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		AllergyRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			allergyText = root["code"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			clinicalStatus = root["clinicalStatus"]?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull,
			recordedAt = parseInstant(root["recordedDate"]?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)

	private fun buildEncounter(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant): EncounterRecord {
		val period = root["period"]?.jsonObject
		return EncounterRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			typeText = root["type"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			startedAt = parseInstant(period?.get("start")?.jsonPrimitive?.contentOrNull),
			endedAt = parseInstant(period?.get("end")?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)
	}

	private fun buildDocument(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		DocumentRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			typeText = root["type"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			documentedAt = parseInstant(root["date"]?.jsonPrimitive?.contentOrNull),
			contentUrl = root["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("attachment")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
			importedAt = now,
		)

	private fun subjectRef(root: JsonObject): String? =
		root["subject"]?.jsonObject?.get("reference")?.jsonPrimitive?.contentOrNull?.removePrefix("Patient/")
}
