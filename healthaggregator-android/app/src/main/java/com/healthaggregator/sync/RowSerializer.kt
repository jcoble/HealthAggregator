package com.healthaggregator.sync

import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.memberProperties

/**
 * Converts Room entities to JSON row maps and back.
 *
 * Strategy: reflective mapping over data-class properties. Each primitive is wrapped as a
 * JsonPrimitive; `Instant` becomes an epoch-millis Long; null fields emit JsonNull.
 *
 * Task 3.3 implements `toRow` (generic) and the four fromXRow helpers for push+pull of
 * labs, vitals, chat_conversations, chat_messages. Task 4.3 fills in the remaining 8 FHIR
 * tables (patient, condition, medication, allergy, encounter, document, diagnostic_report,
 * source_records) when the pull-merge path wires them up.
 */
@Singleton
class RowSerializer @Inject constructor() {

	fun toRow(entity: Any): JsonObject = buildJsonObject {
		entity::class.memberProperties.forEach { prop ->
			@Suppress("UNCHECKED_CAST")
			val value = (prop as kotlin.reflect.KProperty1<Any, *>).get(entity)
			put(prop.name, toJsonValue(value))
		}
	}

	private fun toJsonValue(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
		null -> JsonNull
		is String -> JsonPrimitive(v)
		is Boolean -> JsonPrimitive(v)
		is Int -> JsonPrimitive(v)
		is Long -> JsonPrimitive(v)
		is Double -> JsonPrimitive(v)
		is Float -> JsonPrimitive(v.toDouble())
		is Instant -> JsonPrimitive(v.toEpochMilli())
		else -> JsonPrimitive(v.toString())
	}

	private fun JsonObject.str(name: String): String? {
		val el = get(name) ?: return null
		if (el is JsonNull) return null
		return (el as? JsonPrimitive)?.jsonPrimitive?.content
	}

	private fun JsonObject.strReq(name: String): String =
		str(name) ?: error("missing non-null String field '$name' in row")

	private fun JsonObject.long(name: String): Long? {
		val el = get(name) ?: return null
		if (el is JsonNull) return null
		return (el as? JsonPrimitive)?.longOrNull
	}

	private fun JsonObject.double(name: String): Double? {
		val el = get(name) ?: return null
		if (el is JsonNull) return null
		return (el as? JsonPrimitive)?.doubleOrNull
	}

	private fun JsonObject.bool(name: String): Boolean? {
		val el = get(name) ?: return null
		if (el is JsonNull) return null
		return (el as? JsonPrimitive)?.booleanOrNull
	}

	private fun JsonObject.instant(name: String): Instant? =
		long(name)?.let { Instant.ofEpochMilli(it) }

	fun fromLabRow(r: JsonObject) = LabObservation(
		id = r.long("id") ?: 0L,
		sourceSystem = r.strReq("sourceSystem"),
		sourceName = r.strReq("sourceName"),
		fhirReference = r.strReq("fhirReference"),
		resourceId = r.strReq("resourceId"),
		patientFhirId = r.str("patientFhirId"),
		diagnosticReportReference = r.str("diagnosticReportReference"),
		loincCode = r.str("loincCode"),
		testName = r.strReq("testName"),
		numericValue = r.double("numericValue"),
		textValue = r.str("textValue"),
		unit = r.str("unit"),
		referenceLow = r.double("referenceLow"),
		referenceHigh = r.double("referenceHigh"),
		referenceText = r.str("referenceText"),
		interpretation = r.str("interpretation"),
		effectiveAt = r.instant("effectiveAt"),
		status = r.str("status") ?: "",
		importedAt = r.instant("importedAt") ?: Instant.EPOCH,
		serviceRequestReference = r.str("serviceRequestReference"),
		serviceRequestDisplay = r.str("serviceRequestDisplay"),
		canonicalPanelName = r.str("canonicalPanelName"),
		canonicalTestName = r.str("canonicalTestName"),
	)

	fun fromVitalsRow(r: JsonObject) = VitalsObservation(
		id = r.long("id") ?: 0L,
		sourceSystem = r.strReq("sourceSystem"),
		sourceName = r.strReq("sourceName"),
		fhirReference = r.strReq("fhirReference"),
		resourceId = r.strReq("resourceId"),
		patientFhirId = r.str("patientFhirId"),
		loincCode = r.str("loincCode"),
		code = r.strReq("code"),
		displayName = r.strReq("displayName"),
		numericValue = r.double("numericValue"),
		unit = r.str("unit"),
		componentCode = r.str("componentCode") ?: "",
		effectiveAt = r.instant("effectiveAt"),
		importedAt = r.instant("importedAt") ?: Instant.EPOCH,
	)

	fun fromChatConversationRow(r: JsonObject) = ChatConversation(
		id = r.strReq("id"),
		title = r.strReq("title"),
		createdAt = r.instant("createdAt") ?: Instant.EPOCH,
		updatedAt = r.instant("updatedAt") ?: Instant.EPOCH,
		snapshotText = r.str("snapshotText"),
		snapshotGeneratedAt = r.instant("snapshotGeneratedAt"),
		modelId = r.strReq("modelId"),
	)

	fun fromChatMessageRow(r: JsonObject) = ChatMessage(
		id = r.strReq("id"),
		conversationId = r.strReq("conversationId"),
		role = r.strReq("role"),
		content = r.strReq("content"),
		toolCallsJson = r.str("toolCallsJson"),
		toolCallId = r.str("toolCallId"),
		modelId = r.str("modelId"),
		createdAt = r.instant("createdAt") ?: Instant.EPOCH,
	)
}
