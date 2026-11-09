package com.zjh.timetable.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.TimetableMode
import com.zjh.timetable.data.settings.RecognitionConfig
import com.zjh.timetable.data.settings.TimetableDataStore
import com.zjh.timetable.network.ModelsService
import com.zjh.timetable.ui.data.UiTimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 设置页（详细设计 §8.5）：学期 / 作息 / 默认提醒提前量 / 识别服务（B3）。
 * 学期变更即时落库；作息表编辑后显式保存；识别配置编辑后显式保存。
 */
class SettingsViewModel(
    private val repository: UiTimetableRepository,
    private val dataStore: TimetableDataStore,
) : ViewModel() {

    companion object {
        const val MIN_WEEKS = 10
        const val MAX_WEEKS = 30

        /** 默认提前量选项（分钟，0=准点）。 */
        val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30)

        /** 与 core/data DefaultTimetable.seed 相同的默认作息（恢复默认用）。 */
        fun defaultRows(): List<TimetableRow> = listOf(
            TimetableRow(1, LocalTime.of(8, 0), LocalTime.of(9, 40)),
            TimetableRow(3, LocalTime.of(10, 0), LocalTime.of(11, 40)),
            TimetableRow(5, LocalTime.of(14, 0), LocalTime.of(15, 40)),
            TimetableRow(7, LocalTime.of(16, 0), LocalTime.of(17, 40)),
            TimetableRow(9, LocalTime.of(19, 0), LocalTime.of(20, 40)),
        )
    }

    /** 作息表可编辑行。 */
    data class TimetableRow(
        val periodIndex: Int,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class UiState(
        val loading: Boolean = true,
        val semester: SemesterSetting? = null,
        val rows: List<TimetableRow> = emptyList(),
        val rowsDirty: Boolean = false,
        /** 作息表校验错误（区间非法/行重叠/节次重复） */
        val rowsError: String? = null,
        /** 作息表保存成功标记（UI 提示后清除） */
        val timetableSavedTick: Int = 0,
        // ── 识别服务（§8.5 B3：baseUrl / apiKey / 模型名，显式保存） ──
        val recBaseUrl: String = "",
        val recApiKey: String = "",
        val recModel: String = "",
        val recDirty: Boolean = false,
        /** 识别配置保存成功标记（UI 提示后清除） */
        val recSavedTick: Int = 0,
        // ── 模型列表拉取（B5-FIX2：现拉不持久化） ──
        val recModelsFetching: Boolean = false,
        /** 拉取失败内联提示（带 HTTP 码与响应摘要） */
        val recModelsError: String? = null,
        /** 非空时弹出模型选择弹窗 */
        val recModels: List<String>? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            val semester = repository.observeSemester().first()
            val timetable = repository.observeTimetable().first()
            val recognition = dataStore.recognitionConfig.first()
            _state.update {
                it.copy(
                    loading = false,
                    semester = semester ?: defaultSemester(),
                    rows = timetable.map { row -> TimetableRow(row.periodIndex, row.startTime, row.endTime) },
                    recBaseUrl = recognition?.baseUrl.orEmpty(),
                    recApiKey = recognition?.apiKey.orEmpty(),
                    recModel = recognition?.model.orEmpty(),
                )
            }
        }
    }

    // ── 学期（即时保存） ─────────────────────────────────────────────

    fun setFirstWeekDate(date: LocalDate) = editSemester { it.copy(firstWeekStartDate = date) }

    fun incrementWeeks() = editSemester {
        it.copy(totalWeeks = (it.totalWeeks + 1).coerceAtMost(MAX_WEEKS))
    }

    fun decrementWeeks() = editSemester {
        it.copy(totalWeeks = (it.totalWeeks - 1).coerceAtLeast(MIN_WEEKS))
    }

    fun setTimetableMode(mode: TimetableMode) = editSemester { it.copy(timetableMode = mode) }

    fun setDefaultLead(minutes: Int) = editSemester { it.copy(defaultReminderLeadMinutes = minutes) }

    private fun editSemester(edit: (SemesterSetting) -> SemesterSetting) {
        val before = _state.value.semester ?: defaultSemester()
        val edited = edit(before)
        _state.update { it.copy(semester = edited) }
        viewModelScope.launch { repository.saveSemester(edited) }
    }

    /** 默认学期起点：下周周一（详细设计 §8.5）。 */
    private fun defaultSemester(): SemesterSetting {
        val nextMonday = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY)
        return SemesterSetting(firstWeekStartDate = nextMonday)
    }

    // ── 作息表编辑 ───────────────────────────────────────────────────

    fun addRow() = _state.update {
        val nextIndex = (it.rows.maxOfOrNull { row -> row.periodIndex } ?: 0) + 1
        it.copy(
            rows = it.rows + TimetableRow(nextIndex, LocalTime.of(19, 0), LocalTime.of(20, 40)),
            rowsDirty = true,
            rowsError = null,
        )
    }

    fun removeRow(index: Int) = _state.update {
        it.copy(rows = it.rows.filterIndexed { i, _ -> i != index }, rowsDirty = true, rowsError = null)
    }

    fun updateRowTime(index: Int, start: LocalTime, end: LocalTime) = _state.update {
        it.copy(
            rows = it.rows.mapIndexed { i, row -> if (i == index) row.copy(startTime = start, endTime = end) else row },
            rowsDirty = true,
            rowsError = null,
        )
    }

    /** 保存作息表：区间合法 + 无重叠 + 节次号唯一，事务内全量替换。 */
    fun saveTimetable() {
        val rows = _state.value.rows
        val error = validateRows(rows)
        if (error != null) {
            _state.update { it.copy(rowsError = error) }
            return
        }
        viewModelScope.launch {
            repository.replaceTimetable(rows.map { PeriodTime(it.periodIndex, it.startTime, it.endTime) })
            _state.update { it.copy(rowsDirty = false, rowsError = null, timetableSavedTick = it.timetableSavedTick + 1) }
        }
    }

    fun restoreDefaultTimetable() = _state.update {
        it.copy(rows = defaultRows(), rowsDirty = true, rowsError = null)
    }

    // ── 识别服务配置（§8.5 B3；apiKey 存 DataStore、界面打码） ────────

    fun updateRecBaseUrl(value: String) =
        _state.update { it.copy(recBaseUrl = value, recDirty = true, recModelsError = null) }

    fun updateRecApiKey(value: String) =
        _state.update { it.copy(recApiKey = value, recDirty = true, recModelsError = null) }

    fun updateRecModel(value: String) = _state.update { it.copy(recModel = value, recDirty = true) }

    /** 三项齐全才写入；任一为空清除配置（导入页入口随之置灰）。 */
    fun saveRecognitionConfig() {
        val s = _state.value
        viewModelScope.launch {
            val config = RecognitionConfig(s.recBaseUrl.trim(), s.recApiKey.trim(), s.recModel.trim())
            dataStore.setRecognitionConfig(config)
            _state.update { it.copy(recDirty = false, recSavedTick = it.recSavedTick + 1) }
        }
    }

    // ── 模型列表（B5-FIX2：GET {baseUrl}/models，点选回填模型名） ──

    fun fetchModels() {
        val baseUrl = _state.value.recBaseUrl.trim()
        val apiKey = _state.value.recApiKey.trim()
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            _state.update { it.copy(recModelsError = "请先填写服务地址与 API Key") }
            return
        }
        _state.update { it.copy(recModelsFetching = true, recModelsError = null) }
        viewModelScope.launch {
            val result = ModelsService(baseUrl, apiKey).fetchModels()
            _state.update {
                val models = result.getOrNull().orEmpty()
                if (models.isEmpty()) {
                    it.copy(recModelsFetching = false, recModelsError = result.exceptionOrNull()?.message ?: "服务未返回任何模型")
                } else {
                    it.copy(recModelsFetching = false, recModels = models)
                }
            }
        }
    }

    fun selectModel(name: String) = _state.update {
        it.copy(recModel = name, recDirty = true, recModels = null)
    }

    fun dismissModels() = _state.update { it.copy(recModels = null) }

    /** 无效行 → 错误文案；合法 → null。 */
    private fun validateRows(rows: List<TimetableRow>): String? {
        if (rows.isEmpty()) return "至少保留一行节次"
        if (rows.any { it.endTime <= it.startTime }) return "存在结束时间不晚于开始时间的节次"
        if (rows.distinctBy { it.periodIndex }.size != rows.size) return "节次序号重复"
        val sorted = rows.sortedBy { it.startTime }
        for (i in 1 until sorted.size) {
            if (sorted[i].startTime < sorted[i - 1].endTime) return "节次时间存在重叠"
        }
        return null
    }
}
