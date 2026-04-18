package com.healthaggregator.data

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
	@TypeConverter
	fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

	@TypeConverter
	fun instantToTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()
}
