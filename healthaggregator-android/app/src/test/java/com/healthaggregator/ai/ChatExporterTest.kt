package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChatExporterTest {
	private lateinit var db: AppDatabase
	private lateinit var exporter: ChatExporter

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		exporter = ChatExporter(db.chatDao(), db.labDao())
	}

	@After fun tearDown() { db.close() }

	@Test
	fun markdown_includesHeaderDisclaimerAndMessages() = runTest {
		val now = Instant.parse("2026-02-12T10:00:00Z")
		db.chatDao().upsertConversation(conv("c1", "Metabolic concerns"))
		db.chatDao().upsertMessage(msg("m1", "c1", "user", "What's my A1c doing?", createdAt = now))
		db.chatDao().upsertMessage(msg("m2", "c1", "assistant", "Your A1c is 6.3 [cite:summa/Observation/a1c-recent].", createdAt = now))
		db.labDao().upsert(a1c("a1c-recent", "summa", "Summa Health", 6.3, Instant.parse("2026-02-12T09:00:00Z")))

		val md = exporter.exportMarkdown("c1", now = Instant.parse("2026-04-18T10:00:00Z"))

		assertTrue(md.startsWith("# Metabolic concerns"))
		assertTrue(md.contains("This is not medical advice"))
		assertTrue(md.contains("### You"))
		assertTrue(md.contains("What's my A1c doing?"))
		assertTrue(md.contains("### Assistant"))
		assertTrue(md.contains("[6.3 % on 2026-02-12, Summa Health]"))
		assertFalse(md.contains("[cite:summa/Observation/a1c-recent]"))
	}

	@Test
	fun markdown_unresolvedCitation_fallsBackToRawMarker() = runTest {
		db.chatDao().upsertConversation(conv("c1", "T"))
		db.chatDao().upsertMessage(msg("m1", "c1", "assistant", "Reading [cite:unknown/Observation/xyz] here.", createdAt = Instant.EPOCH))
		val md = exporter.exportMarkdown("c1")
		assertTrue(md.contains("[unknown/Observation/xyz]"))
	}

	@Test
	fun markdown_skipsToolMessages() = runTest {
		db.chatDao().upsertConversation(conv("c1", "T"))
		db.chatDao().upsertMessage(msg("m1", "c1", "user", "check my labs", createdAt = Instant.EPOCH))
		db.chatDao().upsertMessage(msg("m2", "c1", "assistant", "", createdAt = Instant.EPOCH))
		db.chatDao().upsertMessage(msg("m3", "c1", "tool", """{"data":"..."}""", createdAt = Instant.EPOCH, toolCallId = "call_1"))
		db.chatDao().upsertMessage(msg("m4", "c1", "assistant", "All look fine.", createdAt = Instant.EPOCH))
		val md = exporter.exportMarkdown("c1")
		assertFalse("tool JSON must not leak into export", md.contains("\"data\":\"...\""))
		assertTrue(md.contains("All look fine."))
	}

	@Test
	@Ignore("PdfDocument not supported under Robolectric - validated on device")
	fun pdf_returnsValidPdfHeaderBytes() = runTest {
		db.chatDao().upsertConversation(conv("c1", "T"))
		db.chatDao().upsertMessage(msg("m1", "c1", "user", "hi", createdAt = Instant.EPOCH))
		db.chatDao().upsertMessage(msg("m2", "c1", "assistant", "hello", createdAt = Instant.EPOCH))

		val bytes = exporter.exportPdf("c1")
		assertTrue(String(bytes, 0, 4.coerceAtMost(bytes.size)).startsWith("%PDF"))
		assertTrue("empty PDF", bytes.size > 200)
	}

	private fun conv(id: String, title: String) = ChatConversation(
		id = id, title = title,
		createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
		modelId = "gpt-5",
	)

	private fun msg(
		id: String,
		conversationId: String,
		role: String,
		content: String,
		createdAt: Instant,
		toolCallId: String? = null,
	) = ChatMessage(id, conversationId, role, content, null, toolCallId, null, createdAt)

	private fun a1c(
		id: String,
		source: String,
		sourceName: String,
		value: Double,
		at: Instant,
	) = LabObservation(
		sourceSystem = source, sourceName = sourceName,
		fhirReference = "Observation/$id", resourceId = id,
		testName = "Hemoglobin A1c",
		numericValue = value, unit = "%",
		effectiveAt = at, importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
