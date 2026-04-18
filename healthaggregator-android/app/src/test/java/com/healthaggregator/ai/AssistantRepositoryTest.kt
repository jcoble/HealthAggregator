package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.repository.RecordsRepository
import com.healthaggregator.util.SecureStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssistantRepositoryTest {
	private lateinit var db: AppDatabase
	private lateinit var repo: AssistantRepository
	private lateinit var fakeLlm: FakeLlmClient
	private lateinit var secure: SecureStorage

	@Before
	fun setup() {
		val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
		db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
		val records = RecordsRepository(
			labs = db.labDao(),
			vitals = db.vitalsDao(),
			medications = db.medicationDao(),
			conditions = db.conditionDao(),
			allergies = db.allergyDao(),
			encounters = db.encounterDao(),
			documents = db.documentDao(),
			sources = db.medicalDataSourceDao(),
			sourceSummaries = db.sourceSummaryDao(),
			syncJobs = db.syncJobDao(),
			sourceRecords = db.sourceRecordDao(),
		)
		fakeLlm = FakeLlmClient()
		secure = mockk(relaxed = true)
		every { secure.selectedModel } returns "gpt-5"
		every { secure.openAiApiKey } returns "sk-test"
		val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
		repo = AssistantRepository(
			chat = db.chatDao(),
			llm = fakeLlm,
			tools = AssistantTools(records, json),
			snapshots = HealthSnapshotBuilder(
				labs = db.labDao(),
				vitals = db.vitalsDao(),
				medications = db.medicationDao(),
				allergies = db.allergyDao(),
				documents = db.documentDao(),
			),
			secure = secure,
		)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun createConversation_insertsRow() = runTest {
		val c = repo.createConversation()
		val obs = repo.observeConversations().first()
		assertEquals(1, obs.size)
		assertEquals(c.id, obs[0].id)
	}

	@Test
	fun send_persistsUserAndAssistantMessages() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(
			StreamEvent.TokenDelta("Hel"),
			StreamEvent.TokenDelta("lo"),
			StreamEvent.Done,
		)

		repo.send(c.id, "hi there").toList()

		val msgs = db.chatDao().messagesSnapshot(c.id)
		assertEquals(listOf("user", "assistant"), msgs.map { it.role })
		assertEquals("hi there", msgs[0].content)
		assertEquals("Hello", msgs[1].content)
	}

	@Test
	fun send_persistsSnapshotOnFirstTurn_reusesOnSecond() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok"), StreamEvent.Done)

		repo.send(c.id, "first").toList()
		val afterFirst = db.chatDao().getConversation(c.id)!!
		assertTrue(afterFirst.snapshotText!!.contains("# Jesse's Full Health Data Context"))

		val firstSnapshot = afterFirst.snapshotText
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok2"), StreamEvent.Done)
		repo.send(c.id, "second").toList()
		val afterSecond = db.chatDao().getConversation(c.id)!!
		assertEquals(firstSnapshot, afterSecond.snapshotText)
	}

	@Test
	fun send_derivesTitleFromFirstUserMessage() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok"), StreamEvent.Done)
		repo.send(c.id, "what is my A1c").toList()
		assertEquals("what is my A1c", db.chatDao().getConversation(c.id)!!.title)
	}

	@Test
	fun deleteConversation_cascadesAllMessages() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok"), StreamEvent.Done)
		repo.send(c.id, "hi").toList()

		repo.deleteConversation(c.id)
		assertTrue(db.chatDao().messagesSnapshot(c.id).isEmpty())
		assertTrue(repo.observeConversations().first().isEmpty())
	}

	private class FakeLlmClient : LlmClient {
		var scripted: List<StreamEvent> = emptyList()
		override fun stream(
			modelId: String,
			messages: List<LlmMessage>,
			tools: List<ToolSchema>,
			dispatchTool: suspend (String, String) -> String,
		): Flow<StreamEvent> = flow { scripted.forEach { emit(it) } }
	}
}
