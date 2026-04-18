package com.healthaggregator.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncableTablesTest {
	@Test
	fun `all twelve syncable tables present, chat_conversations is LWW`() {
		assertEquals(12, SyncableTable.entries.size)
		val chatConv = SyncableTable.entries.first { it.tableName == "chat_conversations" }
		assertTrue(chatConv.mergeStrategy is MergeStrategy.LastWriteWinsOn)
		val lwwCols = SyncableTable.entries.count { it.mergeStrategy is MergeStrategy.LastWriteWinsOn }
		assertEquals(1, lwwCols, "exactly one table uses LWW")
	}
}
