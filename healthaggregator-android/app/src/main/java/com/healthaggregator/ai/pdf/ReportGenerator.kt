package com.healthaggregator.ai.pdf

import android.content.Context
import com.healthaggregator.data.dao.AllergyDao
import com.healthaggregator.data.dao.LabDao
import com.healthaggregator.data.dao.UserNarrativeDao
import com.healthaggregator.data.dao.VitalsDao
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.VitalsObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.ui.export.ReportType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportGenerator @Inject constructor(
	private val labs: LabDao,
	private val vitals: VitalsDao,
	private val allergies: AllergyDao,
	private val narrativeDao: UserNarrativeDao,
) {
	suspend fun generate(context: Context, type: ReportType, now: Instant = Instant.now()): File {
		val w = PdfWriter()
		w.startPage()

		val today = LocalDate.now(ZoneId.systemDefault())
		val allLabs = labs.getAllSnapshot()
		val allVitals = vitals.getAllSnapshot()
		val allAllergies = allergies.getAllSnapshot()
		val narrative = narrativeDao.getSnapshot()?.text?.trim().orEmpty()

		drawHeader(w, title = titleFor(type), generatedAt = now)

		when (type) {
			ReportType.COMPREHENSIVE -> drawComprehensive(w, today, narrative, allLabs, allVitals, allAllergies)
			ReportType.LABS -> drawLabs(w, allLabs)
			ReportType.RECENT_ABNORMALS -> drawAbnormals(w, allLabs, sinceDate = today.minusYears(2))
			ReportType.ALL_ABNORMALS -> drawAbnormals(w, allLabs, sinceDate = null)
			ReportType.ALLERGIES_AND_NARRATIVE -> drawAllergiesAndNarrative(w, narrative, allAllergies)
			ReportType.VITALS -> drawVitals(w, allVitals)
			ReportType.CARDIOVASCULAR -> drawCardiovascular(w, allLabs, allVitals)
			ReportType.ENDOCRINE -> drawEndocrine(w, allLabs)
		}

		val file = outputFile(context, type, today)
		w.writeTo(file)
		return file
	}

	private fun outputFile(context: Context, type: ReportType, today: LocalDate): File {
		val dir = File(context.cacheDir, "exports").apply { mkdirs() }
		val name = "${today}_${fileSlugFor(type)}_HealthAggregator.pdf"
		return File(dir, name)
	}

	private fun drawHeader(w: PdfWriter, title: String, generatedAt: Instant) {
		w.h1(title)
		w.muted("Generated ${isoDate(generatedAt)} · Health Aggregator")
		w.spacer(8f)
	}

	// ---------- Comprehensive ----------

	private fun drawComprehensive(
		w: PdfWriter,
		today: LocalDate,
		narrative: String,
		allLabs: List<LabObservation>,
		allVitals: List<VitalsObservation>,
		allAllergies: List<com.healthaggregator.data.entities.AllergyRecord>,
	) {
		if (narrative.isNotBlank()) {
			w.h2("Patient-authored notes")
			w.body(narrative)
			w.spacer()
		}
		w.h2("Recent abnormals (last 2 years)")
		val cutoff = today.minusYears(2)
		val recent = allLabs.filter { lab ->
			val date = lab.effectiveAt?.atZone(ZoneOffset.UTC)?.toLocalDate()
			date != null && !date.isBefore(cutoff) && isAbnormal(lab)
		}
		if (recent.isEmpty()) {
			w.muted("No abnormal results in the last 2 years.")
		} else {
			drawAbnormalsGrouped(w, recent)
		}
		w.spacer()

		w.h2("Labs — longitudinal")
		drawLabsByCanonical(w, allLabs)

		w.h2("Vitals / measurements")
		drawVitalsGrouped(w, allVitals)

		w.h2("Allergies")
		drawAllergies(w, allAllergies)
	}

	// ---------- Labs (longitudinal) ----------

	private fun drawLabs(w: PdfWriter, allLabs: List<LabObservation>) {
		w.h2("Laboratory results — all values, grouped by canonical test")
		drawLabsByCanonical(w, allLabs)
	}

	private fun drawLabsByCanonical(w: PdfWriter, allLabs: List<LabObservation>) {
		if (allLabs.isEmpty()) { w.muted("No lab results on record."); return }
		val groups = allLabs.groupBy { it.canonicalTestName ?: it.testName }
		for ((canonical, rows) in groups.toSortedMap()) {
			val refLow = rows.firstOrNull { it.referenceLow != null }?.referenceLow
			val refHigh = rows.firstOrNull { it.referenceHigh != null }?.referenceHigh
			val unit = rows.firstOrNull { it.unit != null }?.unit ?: ""
			val refStr = refRangeString(refLow, refHigh, unit)
			val loincList = rows.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ")
			val header = buildString {
				append(canonical)
				if (loincList.isNotEmpty()) append(" · LOINC $loincList")
				if (refStr.isNotEmpty()) append(" · Ref $refStr")
			}
			w.h3(header)
			rows.sortedBy { it.effectiveAt }.forEach { lab ->
				val date = lab.effectiveAt?.let { isoDate(it) } ?: "n/a"
				val valueStr = formatLabValue(lab)
				val flag = labFlag(lab)
				w.bulletWithFlag(
					dateValue = "$date   $valueStr",
					trailing = "(${lab.sourceName})",
					flag = flag,
				)
			}
			w.spacer(4f)
		}
	}

	// ---------- Abnormals (recent + all) ----------

	private fun drawAbnormals(w: PdfWriter, allLabs: List<LabObservation>, sinceDate: LocalDate?) {
		val filtered = allLabs.filter { lab ->
			if (!isAbnormal(lab)) return@filter false
			if (sinceDate == null) return@filter true
			val d = lab.effectiveAt?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: return@filter false
			!d.isBefore(sinceDate)
		}
		if (filtered.isEmpty()) {
			val span = if (sinceDate == null) "ever recorded" else "in the last 2 years"
			w.muted("No abnormal results $span.")
			return
		}
		val descriptor = if (sinceDate == null) "Every out-of-range result on record" else "Out-of-range results from $sinceDate onward"
		w.h2(descriptor)
		drawAbnormalsGrouped(w, filtered)
	}

	private fun drawAbnormalsGrouped(w: PdfWriter, labs: List<LabObservation>) {
		val bySystem = labs.groupBy { BodySystemClassifier.classify(it) }
		val order = listOf(
			BodySystemClassifier.System.CARDIAC,
			BodySystemClassifier.System.METABOLIC,
			BodySystemClassifier.System.ENDOCRINE,
			BodySystemClassifier.System.RENAL,
			BodySystemClassifier.System.LIVER,
			BodySystemClassifier.System.HEMATOLOGIC,
			BodySystemClassifier.System.INFLAMMATORY,
			BodySystemClassifier.System.OTHER,
		)
		for (system in order) {
			val inSystem = bySystem[system] ?: continue
			w.h3(system.displayName)
			val byTest = inSystem.groupBy { it.canonicalTestName ?: it.testName }
			for ((name, rows) in byTest.toSortedMap()) {
				val refLow = rows.firstOrNull { it.referenceLow != null }?.referenceLow
				val refHigh = rows.firstOrNull { it.referenceHigh != null }?.referenceHigh
				val unit = rows.firstOrNull { it.unit != null }?.unit ?: ""
				val refStr = refRangeString(refLow, refHigh, unit)
				val trailing = if (refStr.isNotEmpty()) " (ref $refStr)" else ""
				w.body("$name$trailing", bold = true)
				rows.sortedByDescending { it.effectiveAt }.forEach { lab ->
					val date = lab.effectiveAt?.let { isoDate(it) } ?: "n/a"
					w.bulletWithFlag(
						dateValue = "$date   ${formatLabValue(lab)}",
						trailing = "(${lab.sourceName})",
						flag = labFlag(lab),
					)
				}
				w.spacer(3f)
			}
			w.spacer(4f)
		}
	}

	// ---------- Allergies + narrative ----------

	private fun drawAllergiesAndNarrative(
		w: PdfWriter,
		narrative: String,
		allAllergies: List<com.healthaggregator.data.entities.AllergyRecord>,
	) {
		w.h2("Patient-authored notes")
		if (narrative.isBlank()) {
			w.muted("(Empty — add in Settings → For my doctor.)")
		} else {
			w.body(narrative)
		}
		w.spacer()
		w.h2("Allergies")
		drawAllergies(w, allAllergies)
	}

	private fun drawAllergies(
		w: PdfWriter,
		allAllergies: List<com.healthaggregator.data.entities.AllergyRecord>,
	) {
		if (allAllergies.isEmpty()) {
			w.muted("None on record.")
			return
		}
		allAllergies.forEach { a ->
			val date = a.recordedAt?.let { isoDate(it) } ?: "n/a"
			w.bullet("${a.allergyText ?: "(unknown)"} · recorded $date · ${a.sourceName}")
		}
	}

	// ---------- Vitals ----------

	private fun drawVitals(w: PdfWriter, allVitals: List<VitalsObservation>) {
		w.h2("Vitals & measurements")
		drawVitalsGrouped(w, allVitals)
	}

	private fun drawVitalsGrouped(w: PdfWriter, allVitals: List<VitalsObservation>) {
		if (allVitals.isEmpty()) {
			w.muted("No vitals on record.")
			return
		}
		// BP is special — pair systolic (8480-6) with diastolic (8462-4) by effectiveAt.
		val bp = allVitals.filter { it.loincCode == "8480-6" || it.loincCode == "8462-4" }
		val rest = allVitals - bp.toSet()
		if (bp.isNotEmpty()) {
			w.h3("Blood Pressure (systolic / diastolic, mmHg)")
			val pairs = bp.groupBy { it.effectiveAt }
			for ((time, rows) in pairs.entries.sortedBy { it.key }) {
				val sys = rows.firstOrNull { it.loincCode == "8480-6" }?.numericValue
				val dia = rows.firstOrNull { it.loincCode == "8462-4" }?.numericValue
				val date = time?.let { isoDate(it) } ?: "n/a"
				val valueStr = when {
					sys != null && dia != null -> "${sys.toInt()}/${dia.toInt()} mmHg"
					sys != null -> "${sys.toInt()}/— mmHg"
					dia != null -> "—/${dia.toInt()} mmHg"
					else -> "—"
				}
				w.bullet("$date   $valueStr   (${rows.first().sourceName})")
			}
			w.spacer(4f)
		}
		val byName = rest.groupBy { it.displayName }
		for ((name, rows) in byName.toSortedMap()) {
			w.h3(name)
			rows.sortedBy { it.effectiveAt }.forEach { v ->
				val date = v.effectiveAt?.let { isoDate(it) } ?: "n/a"
				val valueStr = v.numericValue?.let { "$it ${v.unit ?: ""}".trim() } ?: "—"
				w.bullet("$date   $valueStr   (${v.sourceName})")
			}
			w.spacer(4f)
		}
	}

	// ---------- Cardiovascular snapshot ----------

	private fun drawCardiovascular(
		w: PdfWriter,
		allLabs: List<LabObservation>,
		allVitals: List<VitalsObservation>,
	) {
		val cardiacTests = setOf(
			"Total Cholesterol", "LDL Cholesterol", "HDL Cholesterol", "Triglycerides",
			"Apolipoprotein B", "Lipoprotein(a)",
		)
		val metabolic = setOf("Fasting Glucose", "Glucose", "Hemoglobin A1c")
		val picked = allLabs.filter {
			val name = it.canonicalTestName ?: it.testName
			name in cardiacTests || name in metabolic
		}
		w.h2("Blood pressure")
		val bp = allVitals.filter { it.loincCode == "8480-6" || it.loincCode == "8462-4" }
		if (bp.isEmpty()) w.muted("No BP readings on record.") else drawVitalsGrouped(w, bp)

		w.h2("Lipid panel")
		val lipids = picked.filter {
			(it.canonicalTestName ?: it.testName) in cardiacTests
		}
		if (lipids.isEmpty()) w.muted("No lipid results on record.") else drawLabsByCanonical(w, lipids)

		w.h2("Glucose & A1c")
		val gluc = picked.filter {
			(it.canonicalTestName ?: it.testName) in metabolic
		}
		if (gluc.isEmpty()) w.muted("No glucose/A1c results on record.") else drawLabsByCanonical(w, gluc)
	}

	// ---------- Endocrine snapshot ----------

	private fun drawEndocrine(w: PdfWriter, allLabs: List<LabObservation>) {
		val endocrineTests = setOf(
			"Hemoglobin A1c", "TSH", "Free T4", "Free T3", "Vitamin D 25-Hydroxy",
			"Testosterone", "Estradiol", "DHEA-S", "Cortisol",
		)
		val picked = allLabs.filter {
			(it.canonicalTestName ?: it.testName) in endocrineTests
		}
		w.h2("Glycemic")
		val a1c = picked.filter { (it.canonicalTestName ?: it.testName) == "Hemoglobin A1c" }
		if (a1c.isEmpty()) w.muted("No A1c results on record.") else drawLabsByCanonical(w, a1c)

		w.h2("Thyroid")
		val thyroid = picked.filter {
			val n = it.canonicalTestName ?: it.testName
			n in setOf("TSH", "Free T4", "Free T3")
		}
		if (thyroid.isEmpty()) w.muted("No thyroid results on record.") else drawLabsByCanonical(w, thyroid)

		w.h2("Vitamin D")
		val vitd = picked.filter { (it.canonicalTestName ?: it.testName) == "Vitamin D 25-Hydroxy" }
		if (vitd.isEmpty()) w.muted("No vitamin D results on record.") else drawLabsByCanonical(w, vitd)

		w.h2("Hormones")
		val hormones = picked.filter {
			val n = it.canonicalTestName ?: it.testName
			n in setOf("Testosterone", "Estradiol", "DHEA-S", "Cortisol")
		}
		if (hormones.isEmpty()) w.muted("No hormone results on record.") else drawLabsByCanonical(w, hormones)
	}

	// ---------- Helpers ----------

	private fun formatLabValue(lab: LabObservation): String {
		val numeric = lab.numericValue?.let { n ->
			val unit = lab.unit ?: ""
			"$n $unit".trim()
		}
		return numeric ?: lab.textValue ?: "—"
	}

	private fun labFlag(lab: LabObservation): PdfWriter.ValueFlag {
		val v = lab.numericValue
		if (v != null) {
			if (lab.referenceHigh != null && v > lab.referenceHigh) return PdfWriter.ValueFlag.HIGH
			if (lab.referenceLow != null && v < lab.referenceLow) return PdfWriter.ValueFlag.LOW
		}
		val interp = lab.interpretation?.uppercase() ?: ""
		return when {
			interp.startsWith("H") -> PdfWriter.ValueFlag.HIGH
			interp.startsWith("L") -> PdfWriter.ValueFlag.LOW
			interp.startsWith("A") -> PdfWriter.ValueFlag.HIGH
			else -> PdfWriter.ValueFlag.NORMAL
		}
	}

	private fun refRangeString(low: Double?, high: Double?, unit: String): String {
		val u = if (unit.isNotBlank()) " $unit" else ""
		return when {
			low != null && high != null -> "$low–$high$u"
			high != null -> "≤ $high$u"
			low != null -> "≥ $low$u"
			else -> ""
		}
	}

	private fun titleFor(type: ReportType): String = when (type) {
		ReportType.COMPREHENSIVE -> "Comprehensive Health Report"
		ReportType.LABS -> "Laboratory Results"
		ReportType.RECENT_ABNORMALS -> "Abnormal Lab Results — Last 2 Years"
		ReportType.ALL_ABNORMALS -> "Abnormal Lab Results — All Time"
		ReportType.ALLERGIES_AND_NARRATIVE -> "Allergies & Patient Notes"
		ReportType.VITALS -> "Vitals & Measurements"
		ReportType.CARDIOVASCULAR -> "Cardiovascular Snapshot"
		ReportType.ENDOCRINE -> "Endocrine Snapshot"
	}

	private fun fileSlugFor(type: ReportType): String = when (type) {
		ReportType.COMPREHENSIVE -> "Comprehensive"
		ReportType.LABS -> "Labs"
		ReportType.RECENT_ABNORMALS -> "RecentAbnormals"
		ReportType.ALL_ABNORMALS -> "AllAbnormals"
		ReportType.ALLERGIES_AND_NARRATIVE -> "AllergiesAndNotes"
		ReportType.VITALS -> "Vitals"
		ReportType.CARDIOVASCULAR -> "Cardiovascular"
		ReportType.ENDOCRINE -> "Endocrine"
	}

	private fun isoDate(i: Instant): String =
		DateTimeFormatter.ISO_LOCAL_DATE.format(i.atOffset(ZoneOffset.UTC).toLocalDate())
}
