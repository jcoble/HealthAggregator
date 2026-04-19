package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.UserNarrative
import kotlinx.coroutines.flow.Flow

@Dao
interface UserNarrativeDao {
	@Query("SELECT * FROM user_narrative WHERE id = ${UserNarrative.SINGLETON_ID}")
	fun observe(): Flow<UserNarrative?>

	@Query("SELECT * FROM user_narrative WHERE id = ${UserNarrative.SINGLETON_ID}")
	suspend fun getSnapshot(): UserNarrative?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(narrative: UserNarrative)

	@Query("SELECT * FROM user_narrative")
	suspend fun getAllSnapshot(): List<UserNarrative>
}
