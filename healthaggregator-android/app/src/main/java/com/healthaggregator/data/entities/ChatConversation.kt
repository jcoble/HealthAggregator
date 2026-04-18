package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "chat_conversations",
	indices = [Index(value = ["updatedAt"])],
)
data class ChatConversation(
	@PrimaryKey val id: String,
	val title: String,
	val createdAt: Instant,
	val updatedAt: Instant,
	val snapshotText: String? = null,
	val snapshotGeneratedAt: Instant? = null,
	val modelId: String,
)
