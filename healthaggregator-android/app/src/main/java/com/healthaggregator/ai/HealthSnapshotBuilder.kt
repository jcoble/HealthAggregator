package com.healthaggregator.ai

import com.healthaggregator.data.dao.AllergyDao
import com.healthaggregator.data.dao.ConditionDao
import com.healthaggregator.data.dao.DocumentDao
import com.healthaggregator.data.dao.LabDao
import com.healthaggregator.data.dao.MedicationDao
import com.healthaggregator.data.dao.VitalsDao
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.VitalsObservation
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthSnapshotBuilder @Inject constructor(
	private val labs: LabDao,
	private val vitals: VitalsDao,
	private val medications: MedicationDao,
	private val conditions: ConditionDao,
	private val allergies: AllergyDao,
	private val documents: DocumentDao,
) {
	suspend fun build(now: Instant = Instant.now()): String {
		val allLabs = labs.getAllSnapshot().sortedBy { it.effectiveAt }
		val allVitals = vitals.getAllSnapshot().sortedBy { it.effectiveAt }
		val allMeds = medications.getAllSnapshot()
		val allConditions = conditions.getAllSnapshot()
		val allAllergies = allergies.getAllSnapshot()
		val allDocs = documents.getAllSnapshot()

		val sources = (allLabs.map { it.sourceName } + allVitals.map { it.sourceName })
			.toSortedSet()
			.joinToString(", ")
			.ifEmpty { "none" }

		val (minDate, maxDate) = dateSpan(allLabs, allVitals)
		val bpExists = allVitals.any { it.loincCode == "8462-4" || it.loincCode == "8480-6" }

		val body = buildString {
			appendLine("# Jesse's Full Health Data Context")
			appendLine()
			appendLine("**Generated:** ${fmt(now)}")
			appendLine("**Data span:** ${minDate ?: "n/a"} → ${maxDate ?: "n/a"}")
			appendLine("**Sources:** $sources")
			appendLine()
			appendLine("## Data-model caveats the LLM must know")
			appendLine()
			if (bpExists) {
				appendLine("- Blood pressure is stored as two separate Observations (diastolic LOINC 8462-4 + systolic LOINC 8480-6). Pair them at presentation by matching effectiveAt timestamps.")
			}
			appendLine("- Medication records are bare `Medication` resources without status info — assume \"was taken at some point\" unless the user specifies otherwise. Do not assume current use.")
			appendLine("- Reference ranges vary by lab org; per-row refLow/refHigh are authoritative.")
			appendLine()

			appendLine("## Labs — full history, grouped by canonical test")
			appendLine()
			appendLabsSection(allLabs)

			appendLine("## Vitals — full history")
			appendLine()
			appendVitalsSection(allVitals)

			appendLine("## Medications (note: status unavailable — assume historical)")
			appendLine()
			if (allMeds.isEmpty()) appendLine("_(none)_") else allMeds.forEach { m ->
				appendLine("- ${m.medicationText ?: "(unknown)"} [cite:${m.sourceSystem}/${m.fhirReference}]")
			}
			appendLine()

			appendLine("## Conditions")
			appendLine()
			if (allConditions.isEmpty()) appendLine("_(none)_") else allConditions.forEach { c ->
				val status = c.clinicalStatus?.let { " — $it" } ?: ""
				val onset = c.onsetAt?.let { " (onset ${isoDate(it)})" } ?: ""
				appendLine("- ${c.codeText ?: "(unknown)"}$status$onset [cite:${c.sourceSystem}/${c.fhirReference}]")
			}
			appendLine()

			appendLine("## Allergies")
			appendLine()
			if (allAllergies.isEmpty()) appendLine("_(none)_") else allAllergies.forEach { a ->
				appendLine("- ${a.allergyText ?: "(unknown)"} [cite:${a.sourceSystem}/${a.fhirReference}]")
			}
			appendLine()

			appendLine("## Recent clinical documents")
			appendLine()
			if (allDocs.isEmpty()) appendLine("_(none)_") else allDocs.take(25).forEach { d ->
				// DocumentRecord uses documentedAt (not createdAt) and typeText (not title)
				val date = d.documentedAt?.let { isoDate(it) } ?: "n/a"
				appendLine("- $date: ${d.typeText ?: "(untitled)"} (${d.sourceName}) [cite:${d.sourceSystem}/${d.fhirReference}]")
			}
			appendLine()
		}

		return enforceCap(body, allLabs)
	}

	private fun StringBuilder.appendLabsSection(allLabs: List<LabObservation>) {
		if (allLabs.isEmpty()) { appendLine("_(none)_"); appendLine(); return }
		val groups: Map<String, List<LabObservation>> = allLabs.groupBy { lab ->
			lab.canonicalTestName ?: lab.testName
		}
		for ((canonical, rows) in groups.toSortedMap()) {
			val loincList = rows.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ").ifEmpty { "no LOINC" }
			val refLow = rows.firstOrNull { it.referenceLow != null }?.referenceLow
			val refHigh = rows.firstOrNull { it.referenceHigh != null }?.referenceHigh
			val unit = rows.firstOrNull { it.unit != null }?.unit ?: ""
			val refStr = when {
				refLow != null && refHigh != null -> " | Ref: $refLow–$refHigh $unit"
				refHigh != null -> " | Ref: ≤ $refHigh $unit"
				refLow != null -> " | Ref: ≥ $refLow $unit"
				else -> ""
			}
			appendLine("### $canonical (LOINC $loincList)$refStr")
			rows.sortedBy { it.effectiveAt }.forEach { lab ->
				val date = lab.effectiveAt?.let { isoDate(it) } ?: "n/a"
				val valueStr = lab.numericValue?.let { "$it ${lab.unit ?: ""}" }?.trim() ?: (lab.textValue ?: "—")
				val flag = classifyRange(lab.numericValue, lab.referenceLow, lab.referenceHigh)
				val flagStr = if (flag.label.isEmpty()) "" else " ${flag.label}"
				appendLine("- $date: $valueStr$flagStr (${lab.sourceName}) [cite:${lab.sourceSystem}/${lab.fhirReference}]")
			}
			appendLine()
		}
	}

	private fun StringBuilder.appendVitalsSection(allVitals: List<VitalsObservation>) {
		if (allVitals.isEmpty()) { appendLine("_(none)_"); appendLine(); return }
		val groups: Map<String, List<VitalsObservation>> = allVitals.groupBy { it.displayName }
		for ((name, rows) in groups.toSortedMap()) {
			val loinc = rows.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ")
			appendLine("### $name${if (loinc.isNotEmpty()) " (LOINC $loinc)" else ""}")
			rows.sortedBy { it.effectiveAt }.forEach { v ->
				val date = v.effectiveAt?.let { isoDate(it) } ?: "n/a"
				val valueStr = v.numericValue?.let { "$it ${v.unit ?: ""}" }?.trim() ?: "—"
				val comp = v.componentCode?.let { " [$it]" } ?: ""
				appendLine("- $date: $valueStr$comp (${v.sourceName}) [cite:${v.sourceSystem}/${v.fhirReference}]")
			}
			appendLine()
		}
	}

	private fun dateSpan(
		labs: List<LabObservation>,
		vitals: List<VitalsObservation>,
	): Pair<String?, String?> {
		val all = labs.mapNotNull { it.effectiveAt } + vitals.mapNotNull { it.effectiveAt }
		if (all.isEmpty()) return null to null
		return isoDate(all.min()) to isoDate(all.max())
	}

	private fun enforceCap(body: String, allLabs: List<LabObservation>): String {
		val approxTokens = body.length / 4
		if (approxTokens <= MAX_TOKENS_ESTIMATE) return body

		val truncated = buildString {
			appendLine("> NOTE: dataset exceeded ${MAX_TOKENS_ESTIMATE}-token target; oldest lab readings omitted per test.")
			appendLine()
			append(body.substringBefore("## Labs"))
			appendLine("## Labs — full history (TRUNCATED, recent-${MIN_PER_TEST}-per-test only)")
			appendLine()

			val groups = allLabs.groupBy { it.canonicalTestName ?: it.testName }
			for ((canonical, rows) in groups.toSortedMap()) {
				val sortedRows = rows.sortedByDescending { it.effectiveAt }
				val kept = sortedRows.take(MIN_PER_TEST).sortedBy { it.effectiveAt }
				val dropped = sortedRows.size - kept.size
				val loincList = kept.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ").ifEmpty { "no LOINC" }
				appendLine("### $canonical (LOINC $loincList)")
				kept.forEach { lab ->
					val date = lab.effectiveAt?.let { isoDate(it) } ?: "n/a"
					val valueStr = lab.numericValue?.let { "$it ${lab.unit ?: ""}" }?.trim() ?: (lab.textValue ?: "—")
					val flag = classifyRange(lab.numericValue, lab.referenceLow, lab.referenceHigh)
					val flagStr = if (flag.label.isEmpty()) "" else " ${flag.label}"
					appendLine("- $date: $valueStr$flagStr (${lab.sourceName}) [cite:${lab.sourceSystem}/${lab.fhirReference}]")
				}
				if (dropped > 0) appendLine("- _(+$dropped older readings omitted)_")
				appendLine()
			}
			append(body.substringAfter("## Vitals"))
		}
		return truncated
	}

	private fun isoDate(i: Instant): String =
		DateTimeFormatter.ISO_LOCAL_DATE.format(i.atOffset(ZoneOffset.UTC).toLocalDate())

	private fun fmt(i: Instant): String =
		DateTimeFormatter.ISO_INSTANT.format(i)

	companion object {
		const val MAX_TOKENS_ESTIMATE = 200_000
		const val MIN_PER_TEST = 20
	}
}
