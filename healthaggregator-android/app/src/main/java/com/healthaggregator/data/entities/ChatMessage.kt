package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "chat_messages",
	foreignKeys = [
		ForeignKey(
			entity = ChatConversation::class,
			parentColumns = ["id"],
			childColumns = ["conversationId"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index(value = ["conversationId", "createdAt"])],
)
data class ChatMessage(
	@PrimaryKey val id: String,
	val conversationId: String,
	val role: String, // "user" | "assistant" | "tool" | "system-hidden"
	val content: String,
	val toolCallsJson: String? = null,
	val toolCallId: String? = null,
	val modelId: String? = null,
	val createdAt: Instant,
)
