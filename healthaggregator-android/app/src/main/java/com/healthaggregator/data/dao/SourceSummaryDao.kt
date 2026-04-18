package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.healthaggregator.data.entities.SourceSummary
import kotlinx.coroutines.flow.Flow

/**
 * Derives the home dashboard sources from actual data across all clinical tables,
 * joining to medical_data_sources only for optional color overrides.
 */
@Dao
interface SourceSummaryDao {
	@Query(
		"""
		SELECT rollup.sourceSystem AS sourceSystem,
		       rollup.displayName AS displayName,
		       rollup.recordCount AS recordCount,
		       mds.colorOverride AS colorOverride
		FROM (
			SELECT sourceSystem, MAX(sourceName) AS displayName, COUNT(*) AS recordCount
			FROM (
				SELECT sourceSystem, sourceName FROM lab_observations
				UNION ALL SELECT sourceSystem, sourceName FROM vitals_observations
				UNION ALL SELECT sourceSystem, sourceName FROM conditions
				UNION ALL SELECT sourceSystem, sourceName FROM medications
				UNION ALL SELECT sourceSystem, sourceName FROM allergies
				UNION ALL SELECT sourceSystem, sourceName FROM encounters
				UNION ALL SELECT sourceSystem, sourceName FROM documents
				UNION ALL SELECT sourceSystem, sourceName FROM diagnostic_reports
			)
			GROUP BY sourceSystem
		) AS rollup
		LEFT JOIN medical_data_sources AS mds
		  ON mds.sourceSystem = rollup.sourceSystem
		ORDER BY rollup.displayName
		"""
	)
	fun observeSources(): Flow<List<SourceSummary>>
}
