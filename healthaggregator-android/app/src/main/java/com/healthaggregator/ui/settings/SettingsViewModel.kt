package com.healthaggregator.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.ai.AssistantRepository
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.dao.MedicalDataSourceDao
import com.healthaggregator.data.dao.UserNarrativeDao
import com.healthaggregator.data.entities.MedicalDataSource
import com.healthaggregator.data.entities.UserNarrative
import java.time.Instant
import com.healthaggregator.data.repository.RecordsRepository
import com.healthaggregator.data.repository.SyncRepository
import com.healthaggregator.sync.HealthConnectReader
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
	val healthConnectAvailable: Boolean = true,
	val permissionsGranted: Boolean = false,
	val sources: List<MedicalDataSource> = emptyList(),
	val totalRecords: Int = 0,
	val dbSizeBytes: Long = 0L,
	val narrative: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val records: RecordsRepository,
	private val sync: SyncRepository,
	private val reader: HealthConnectReader,
	private val mdsDao: MedicalDataSourceDao,
	private val narrativeDao: UserNarrativeDao,
	private val db: AppDatabase,
	@ApplicationContext private val context: Context,
	val secureStorage: SecureStorage,
	private val assistantRepo: AssistantRepository,
) : ViewModel() {

	private val _permission = MutableStateFlow(false)
	private val _dbSize = MutableStateFlow(currentDbSize())

	val uiState: StateFlow<SettingsUiState> = combine(
		records.observeSources(),
		totalsFlow(),
		_permission,
		_dbSize,
		narrativeDao.observe().map { it?.text.orEmpty() },
	) { sources, total, perm, size, narrative ->
		SettingsUiState(
			healthConnectAvailable = reader.isAvailable(),
			permissionsGranted = perm,
			sources = sources,
			totalRecords = total,
			dbSizeBytes = size,
			narrative = narrative,
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

	init {
		viewModelScope.launch { _permission.value = reader.hasAllPermissions() }
	}

	fun refreshPermissions() {
		viewModelScope.launch { _permission.value = reader.hasAllPermissions() }
	}

	fun syncNow() {
		viewModelScope.launch {
			sync.sync()
			_dbSize.value = currentDbSize()
		}
	}

	fun resetDatabase() {
		viewModelScope.launch {
			sync.clearAllData()
			_dbSize.value = currentDbSize()
		}
	}

	fun updateSourceDisplayName(id: Long, newDisplayName: String) {
		viewModelScope.launch { mdsDao.updateDisplayName(id, newDisplayName) }
	}

	fun clearChatHistory() {
		viewModelScope.launch { assistantRepo.clearAllChatHistory() }
	}

	fun updateNarrative(text: String) {
		viewModelScope.launch {
			narrativeDao.upsert(UserNarrative(text = text, updatedAt = Instant.now()))
		}
	}

	private fun totalsFlow(): Flow<Int> = combine(
		records.countLabs(), records.countVitals(), records.countMedications(),
		records.countConditions(), records.countAllergies(),
		records.countEncounters(), records.countDocuments(),
	) { arr -> arr.sum() }

	private fun currentDbSize(): Long =
		runCatching { context.getDatabasePath("healthaggregator.db").length() }.getOrDefault(0L)
}
