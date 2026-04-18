package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChatDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: ChatDao

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.chatDao()
	}

	@After fun tearDown() { db.close() }

	@Test
	fun upsertConversation_andObserve_returnsIt() = runTest {
		val c = conversation("c1", "My Health Chat")
		dao.upsertConversation(c)
		val observed = dao.observeConversations().first()
		assertEquals(1, observed.size)
		assertEquals("My Health Chat", observed[0].title)
	}

	@Test
	fun observeConversations_ordersByUpdatedAtDescending() = runTest {
		dao.upsertConversation(conversation("a", "A", updatedAtMillis = 100))
		dao.upsertConversation(conversation("b", "B", updatedAtMillis = 300))
		dao.upsertConversation(conversation("c", "C", updatedAtMillis = 200))
		val obs = dao.observeConversations().first()
		assertEquals(listOf("b", "c", "a"), obs.map { it.id })
	}

	@Test
	fun upsertMessages_andObserve_returnsInCreatedAtOrder() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.upsertMessages(listOf(
			message("m2", "c1", "user", "second", createdAtMillis = 200),
			message("m1", "c1", "user", "first", createdAtMillis = 100),
			message("m3", "c1", "assistant", "third", createdAtMillis = 300),
		))
		val msgs = dao.observeMessages("c1").first()
		assertEquals(listOf("first", "second", "third"), msgs.map { it.content })
	}

	@Test
	fun deleteConversation_cascadesMessages() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.upsertMessage(message("m1", "c1", "user", "hi"))
		dao.upsertMessage(message("m2", "c1", "assistant", "hello"))

		dao.deleteConversation("c1")

		assertNull(dao.getConversation("c1"))
		assertTrue(dao.messagesSnapshot("c1").isEmpty())
	}

	@Test
	fun renameConversation_updatesTitleAndTimestamp() = runTest {
		dao.upsertConversation(conversation("c1", "old", updatedAtMillis = 100))
		dao.renameConversation("c1", "new", Instant.ofEpochMilli(500))
		val c = dao.getConversation("c1")!!
		assertEquals("new", c.title)
		assertEquals(500L, c.updatedAt.toEpochMilli())
	}

	@Test
	fun setSnapshot_persistsSnapshotText() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.setSnapshot("c1", "# snapshot body", Instant.ofEpochMilli(777))
		val c = dao.getConversation("c1")!!
		assertEquals("# snapshot body", c.snapshotText)
		assertEquals(777L, c.snapshotGeneratedAt!!.toEpochMilli())
	}

	@Test
	fun toolCallPayload_roundTrips() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.upsertMessage(message("m1", "c1", "assistant", "", toolCallsJson = """[{"id":"call_1","name":"getRawObservation"}]"""))
		dao.upsertMessage(message("m2", "c1", "tool", "{\"raw\":\"...\"}", toolCallId = "call_1"))

		val msgs = dao.messagesSnapshot("c1")
		assertEquals(2, msgs.size)
		assertTrue(msgs[0].toolCallsJson!!.contains("getRawObservation"))
		assertEquals("call_1", msgs[1].toolCallId)
	}

	private fun conversation(
		id: String,
		title: String,
		updatedAtMillis: Long = 0,
		modelId: String = "gpt-5",
	) = ChatConversation(
		id = id,
		title = title,
		createdAt = Instant.ofEpochMilli(updatedAtMillis),
		updatedAt = Instant.ofEpochMilli(updatedAtMillis),
		modelId = modelId,
	)

	private fun message(
		id: String,
		conversationId: String,
		role: String,
		content: String,
		createdAtMillis: Long = 0,
		toolCallsJson: String? = null,
		toolCallId: String? = null,
	) = ChatMessage(
		id = id,
		conversationId = conversationId,
		role = role,
		content = content,
		toolCallsJson = toolCallsJson,
		toolCallId = toolCallId,
		createdAt = Instant.ofEpochMilli(createdAtMillis),
	)
}
