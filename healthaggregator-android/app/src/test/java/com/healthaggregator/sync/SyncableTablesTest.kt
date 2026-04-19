package com.healthaggregator.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncableTablesTest {
	@Test
	fun `LWW tables are chat_conversations and user_narrative`() {
		assertEquals(12, SyncableTable.entries.size)
		val chatConv = SyncableTable.entries.first { it.tableName == "chat_conversations" }
		assertTrue(chatConv.mergeStrategy is MergeStrategy.LastWriteWinsOn)
		val narrative = SyncableTable.entries.first { it.tableName == "user_narrative" }
		assertTrue(narrative.mergeStrategy is MergeStrategy.LastWriteWinsOn)
		val lwwCols = SyncableTable.entries.count { it.mergeStrategy is MergeStrategy.LastWriteWinsOn }
		assertEquals(2, lwwCols, "exactly two tables use LWW")
	}
}
