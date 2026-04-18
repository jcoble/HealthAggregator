package com.healthaggregator.ai

import com.healthaggregator.data.repository.RecordsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class ToolSchema(
	val name: String,
	val description: String,
	val parameters: JsonObject,
)

@Singleton
class AssistantTools @Inject constructor(
	private val records: RecordsRepository,
	private val json: Json,
) {
	val schemas: List<ToolSchema> = listOf(
		ToolSchema(
			name = "getRawObservation",
			description = "Return the raw FHIR JSON text for a specific resource. Use this to verify a specific reading or inspect fields not compacted into the HealthReport.",
			parameters = buildJsonObject {
				put("type", "object")
				put("properties", buildJsonObject {
					put("sourceSystem", buildJsonObject {
						put("type", "string")
						put("description", "Source system id — e.g., 'cleveland-clinic'")
					})
					put("fhirRef", buildJsonObject {
						put("type", "string")
						put("description", "FHIR reference — e.g., 'Observation/abc123'")
					})
				})
				put("required", buildJsonArray { add("sourceSystem"); add("fhirRef") })
			},
		),
		ToolSchema(
			name = "getPanelComponents",
			description = "Return all components of a lab panel (e.g., every test in one CMP order). Use when the user asks about a specific panel order or you need component grouping.",
			parameters = buildJsonObject {
				put("type", "object")
				put("properties", buildJsonObject {
					put("serviceRequestRef", buildJsonObject {
						put("type", "string")
						put("description", "FHIR ServiceRequest reference — e.g., 'ServiceRequest/bmp-1'")
					})
				})
				put("required", buildJsonArray { add("serviceRequestRef") })
			},
		),
		ToolSchema(
			name = "searchFreeText",
			description = "Case-insensitive substring search over all stored raw FHIR resources. Use this to find clinical notes, impressions, or DocumentReference content not serialized in the HealthReport.",
			parameters = buildJsonObject {
				put("type", "object")
				put("properties", buildJsonObject {
					put("query", buildJsonObject {
						put("type", "string")
						put("description", "Substring to search for (case-insensitive).")
					})
					put("limit", buildJsonObject {
						put("type", "integer")
						put("description", "Max results to return; default 20.")
					})
				})
				put("required", buildJsonArray { add("query") })
			},
		),
	)

	suspend fun dispatch(name: String, argsJson: String): String = try {
		val args = json.decodeFromString<JsonObject>(argsJson)
		when (name) {
			"getRawObservation" -> {
				val src = args.stringOrError("sourceSystem")
				val ref = args.stringOrError("fhirRef")
				val raw = records.findRawJson(src, ref)
				if (raw == null) """{"error":"not_found","sourceSystem":"$src","fhirRef":"$ref"}"""
				else raw
			}
			"getPanelComponents" -> {
				val sr = args.stringOrError("serviceRequestRef")
				val labs = records.observeLabsByServiceRequest(sr).first()
				buildJsonArray {
					labs.forEach { lab ->
						add(buildJsonObject {
							put("testName", lab.canonicalTestName ?: lab.testName)
							put("loincCode", JsonPrimitive(lab.loincCode))
							put("value", JsonPrimitive(lab.numericValue))
							put("unit", JsonPrimitive(lab.unit))
							put("refLow", JsonPrimitive(lab.referenceLow))
							put("refHigh", JsonPrimitive(lab.referenceHigh))
							put("effectiveAt", JsonPrimitive(lab.effectiveAt?.toString()))
							put("cite", "${lab.sourceSystem}/${lab.fhirReference}")
						})
					}
				}.toString()
			}
			"searchFreeText" -> {
				val query = args.stringOrError("query")
				val limit = (args["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 20
				val matches = records.searchRawJsonText(query, limit)
				buildJsonArray {
					matches.forEach { sr ->
						add(buildJsonObject {
							put("resourceType", sr.resourceType)
							put("source", sr.sourceSystem)
							put("fhirRef", sr.fhirReference)
							put("excerpt", excerpt(sr.rawJson, query))
						})
					}
				}.toString()
			}
			else -> """{"error":"unknown_tool","name":"$name"}"""
		}
	} catch (e: Exception) {
		"""{"error":"${e.javaClass.simpleName}","message":"${e.message?.replace("\"", "\\\"")}"}"""
	}

	private fun JsonObject.stringOrError(key: String): String =
		(this[key] as? JsonPrimitive)?.content ?: error("missing required arg: $key")

	private fun excerpt(raw: String, query: String, window: Int = 140): String {
		val idx = raw.indexOf(query, ignoreCase = true)
		if (idx == -1) return raw.take(window)
		val start = (idx - window / 2).coerceAtLeast(0)
		val end = (idx + query.length + window / 2).coerceAtMost(raw.length)
		return raw.substring(start, end)
	}
}
