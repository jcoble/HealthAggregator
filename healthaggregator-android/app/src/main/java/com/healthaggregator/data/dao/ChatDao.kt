package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ChatDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertConversation(conversation: ChatConversation)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessage(message: ChatMessage)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessages(messages: List<ChatMessage>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertMessagesIgnore(rows: List<ChatMessage>): List<Long>

	@Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
	fun observeConversations(): Flow<List<ChatConversation>>

	@Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
	suspend fun getConversation(id: String): ChatConversation?

	@Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
	fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

	@Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
	suspend fun messagesSnapshot(conversationId: String): List<ChatMessage>

	@Query("UPDATE chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
	suspend fun renameConversation(id: String, title: String, updatedAt: Instant)

	@Query("UPDATE chat_conversations SET updatedAt = :updatedAt WHERE id = :id")
	suspend fun touch(id: String, updatedAt: Instant)

	@Query("UPDATE chat_conversations SET snapshotText = :text, snapshotGeneratedAt = :at WHERE id = :id")
	suspend fun setSnapshot(id: String, text: String, at: Instant)

	@Query("DELETE FROM chat_messages WHERE id = :id")
	suspend fun deleteMessage(id: String)

	@Query("DELETE FROM chat_conversations WHERE id = :id")
	suspend fun deleteConversation(id: String)

	@Query("SELECT * FROM chat_conversations")
	suspend fun getAllConversationsSnapshot(): List<ChatConversation>

	@Query("SELECT * FROM chat_messages")
	suspend fun getAllMessagesSnapshot(): List<ChatMessage>

	@Query("DELETE FROM chat_conversations")
	suspend fun deleteAllConversations()
}
