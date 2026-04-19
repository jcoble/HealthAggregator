package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Single-row table holding user-authored prose the doctor should see — allergies-narrative
 * page one of PDF exports, plus context for LLM sessions. Kept as a singleton row
 * (id = SINGLETON_ID). Synced phone↔laptop with last-write-wins on updatedAt.
 */
@Entity(tableName = "user_narrative")
data class UserNarrative(
	@PrimaryKey val id: Long = SINGLETON_ID,
	val text: String,
	val updatedAt: Instant,
) {
	companion object {
		const val SINGLETON_ID: Long = 1L
	}
}
