package com.healthaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scratch")
data class ScratchEntity(
	@PrimaryKey val id: Long = 0L,
	val placeholder: String = "",
)
