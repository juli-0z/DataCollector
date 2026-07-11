package cn.zjl.datacollector.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OperationLogViewModel(
    private val logStore: OperationLogStore
) : ViewModel() {

    // 日志列表状态 — Compose 通过 collectAsState() 观察
    private val _uiState = MutableStateFlow(OperationLogUiState())
    val uiState: StateFlow<OperationLogUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = logStore.loadHistory()
            _uiState.value = OperationLogUiState(
                records = records,
                isEmpty = records.isEmpty(),
                summary = buildSummary(records),
                categorySummary = buildCategorySummary(records)
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            logStore.clearHistory()
            loadHistory()
        }
    }
    fun deleteRecord(record: OperationLogStore.LogRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            logStore.deleteRecord(record)
            loadHistory()
        }
    }
    private fun buildSummary(records: List<OperationLogStore.LogRecord>): String {
        if (records.isEmpty()) return ""
        val latest = records[0]
        return "共 ${records.size} 条记录 · 最近更新 ${formatTime(latest.createdAt)}"
    }

    private fun buildCategorySummary(records: List<OperationLogStore.LogRecord>): String {
        var collectionCount = 0; var deviceCount = 0; var templateCount = 0
        var uploadCount = 0; var projectCount = 0; var settingsCount = 0
        for (record in records) {
            when (record.category) {
                OperationLogStore.CATEGORY_COLLECTION -> collectionCount++
                OperationLogStore.CATEGORY_DEVICE -> deviceCount++
                OperationLogStore.CATEGORY_TEMPLATE -> templateCount++
                OperationLogStore.CATEGORY_UPLOAD -> uploadCount++
                OperationLogStore.CATEGORY_PROJECT -> projectCount++
                OperationLogStore.CATEGORY_SETTINGS -> settingsCount++
            }
        }
        return "采集 $collectionCount · 设备 $deviceCount · 模板 $templateCount · 上传 $uploadCount · 项目 $projectCount · 设置 $settingsCount"
    }

    companion object {
        fun formatTime(timeMillis: Long): String {
            if (timeMillis <= 0L) return "--"
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timeMillis))
        }

        fun getCategoryLabel(category: String?): String {
            val safe = category ?: ""
            return when {
                OperationLogStore.CATEGORY_COLLECTION == safe -> "采集"
                OperationLogStore.CATEGORY_DEVICE == safe -> "设备"
                OperationLogStore.CATEGORY_TEMPLATE == safe -> "模板"
                OperationLogStore.CATEGORY_UPLOAD == safe -> "上传"
                OperationLogStore.CATEGORY_PROJECT == safe -> "项目"
                OperationLogStore.CATEGORY_SETTINGS == safe -> "设置"
                else -> "采集"
            }
        }
    }
}

// UiState 数据类 — Kotlin data class
data class OperationLogUiState(
    val records: List<OperationLogStore.LogRecord> = emptyList(),
    val isEmpty: Boolean = true,
    val summary: String = "",
    val categorySummary: String = ""
)

// Factory — 因为 OperationLogStore 需要 Context
class OperationLogViewModelFactory(
    private val logStore: OperationLogStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OperationLogViewModel(logStore) as T
    }
}