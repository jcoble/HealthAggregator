package com.healthaggregator.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Rows are serialized as `JsonObject` maps (column name → JSON primitive/null). */
typealias SyncRow = JsonObject

@Serializable
data class VersionResponse(
	val schema_version: Int,
	val daemon_version: String,
)

@Serializable
data class PushRequest(
	val batch_id: String,
	val rows_by_table: Map<String, List<SyncRow>>,
)

@Serializable
data class PushResponse(
	val inserted_by_table: Map<String, Int>,
	val ignored_by_table: Map<String, Int>,
)

@Serializable
data class PullResponse(
	val rows_by_table: Map<String, List<SyncRow>>,
)

@Serializable
data class MigrateRequest(
	val from_version: Int,
	val to_version: Int,
	val sql: String,
)

@Serializable
data class MigrateResponse(
	val applied: List<AppliedMigration>,
)

@Serializable
data class AppliedMigration(
	val from: Int,
	val to: Int,
)
