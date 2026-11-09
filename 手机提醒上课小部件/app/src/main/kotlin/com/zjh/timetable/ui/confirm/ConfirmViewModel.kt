package com.zjh.timetable.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zjh.timetable.domain.calc.CustomWeeks
import com.zjh.timetable.domain.calc.PeriodMapper
import com.zjh.timetable.domain.calc.WeekCalculator
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.TimetableMode
import com.zjh.timetable.domain.model.WeekPattern
import com.zjh.timetable.network.RecognitionDraftCodec
import com.zjh.timetable.ui.data.UiTimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 导入确认页（详细设计 §5.5）：草稿行内编辑、低置信度标黄、冲突 ⚠、
 * 二次确认后整批入库（addCourses 单事务，全局约束 §9.2）或全部丢弃（删草稿不写库，§9.5）。
 */
class ConfirmViewModel(
    private val repository: UiTimetableRepository,
    private val draftId: Long,
) : ViewModel() {

    /** 单条待导入课程的可编辑态。 */
    data class RowState(
        // ── §5.2 校验标记（原始，标黄依据） ──
        val lowConfidence: Boolean = false,
        val periodMappingFailed: Boolean = false,
        /** weeksRaw 与 weeksParsed 冲突；用户修改周次后清除 */
        val weeksConflict: Boolean = false,
        /** 识别原文周次串，供用户对照修正（概要设计 §6.1） */
        val weeksRaw: String? = null,
        // ── 可编辑字段 ──
        val name: String,
        val dayOfWeek: Int,
        val usePeriod: Boolean,
        val startPeriod: Int = 1,
        val endPeriod: Int = 1,
        val startTime: LocalTime = LocalTime.of(8, 0),
        val endTime: LocalTime = LocalTime.of(9, 40),
        val weekPattern: WeekPattern = WeekPattern.ALL,
        val customWeeks: Set<Int> = emptySet(),
        val location: String = "",
        val teacher: String = "",
        /** 识别结果完全无时间信息（无节次且无时刻），用户补全前阻止导入（禁止编造） */
        val missingTime: Boolean = false,
        // ── 派生（每次编辑后重算） ──
        /** 与既有课程/其他行的冲突名（红色 ⚠） */
        val conflictWith: List<String> = emptyList(),
        /** 节次映射失败且时刻不可用等，阻止导入 */
        val timeError: String? = null,
    ) {
        /** 标黄（§5.5：confidence<0.6/关键字段缺失/节次映射失败/周次冲突）。 */
        val needsReview: Boolean
            get() = lowConfidence || periodMappingFailed || weeksConflict || missingTime

        val weekSummary: String
            get() = when (weekPattern) {
                WeekPattern.ALL -> "每周"
                WeekPattern.ODD -> "单周"
                WeekPattern.EVEN -> "双周"
                WeekPattern.CUSTOM -> "自定义：" + CustomWeeks.encode(customWeeks).ifBlank { "未选" }
            }
    }

    data class UiState(
        val loading: Boolean = true,
        val rows: List<RowState> = emptyList(),
        /** §5.2 规则 2 丢弃计数，确认页顶部提示 */
        val droppedCount: Int = 0,
        val totalWeeks: Int = 20,
        /** 导入完成后跳转的教学周；学期外为 null → 周课表按 1 显示 */
        val currentWeek: Int? = null,
        val showImportConfirm: Boolean = false,
        val showDiscardConfirm: Boolean = false,
        /** 导入完成 → 跳周课表并 Toast「已导入 N 门课程」 */
        val importedCount: Int? = null,
        /** 全部丢弃完成 → 返回（不写库） */
        val discarded: Boolean = false,
    ) {
        /** 确认导入可用性：全部行名称非空且时间可解析。 */
        val canImport: Boolean
            get() = rows.isNotEmpty() && rows.all { it.name.isNotBlank() && it.timeError == null }

        val needsReviewCount: Int
            get() = rows.count { it.needsReview }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var timetable: List<PeriodTime> = emptyList()
    private var existingCourses: List<Course> = emptyList()

    init {
        viewModelScope.launch {
            val draft = repository.draft(draftId)
            if (draft == null) {
                // 草稿已被清理（App 被杀等，§5.4）：无可确认内容，退回
                _state.update { it.copy(loading = false, discarded = true) }
                return@launch
            }
            val semester = repository.observeSemester().first()
            val timetableNow = repository.observeTimetable().first()
            val courses = repository.observeCourses().first()
            timetable = timetableNow
            existingCourses = courses
            val outcome = RecognitionDraftCodec.decode(draft.coursesJson)
            _state.update {
                it.copy(
                    loading = false,
                    rows = outcome.rows.map { row -> toRowState(row, semester, timetableNow) },
                    droppedCount = outcome.droppedCount,
                    totalWeeks = semester?.totalWeeks ?: 20,
                    currentWeek = semester?.let { s ->
                        WeekCalculator.currentWeek(s.firstWeekStartDate, java.time.LocalDate.now(), s.totalWeeks)
                    },
                )
            }
            refreshConflicts()
        }
    }

    // ── 行内编辑（§5.5） ─────────────────────────────────────────────

    fun updateName(index: Int, value: String) = editRow(index) { it.copy(name = value) }

    fun updateDay(index: Int, day: Int) = editRow(index) { it.copy(dayOfWeek = day) }

    fun updateUsePeriod(index: Int, usePeriod: Boolean) = editRow(index) { it.copy(usePeriod = usePeriod) }

    fun updatePeriods(index: Int, start: Int, end: Int) =
        editRow(index) { it.copy(startPeriod = start, endPeriod = end) }

    fun updateTimes(index: Int, start: LocalTime, end: LocalTime) =
        editRow(index) { it.copy(startTime = start, endTime = end, missingTime = false) }

    fun updateWeekPattern(index: Int, pattern: WeekPattern) =
        editRow(index) { it.copy(weekPattern = pattern) }

    /** 勾选/取消单个周次（自动切 CUSTOM），并清除周次冲突标记。 */
    fun toggleCustomWeek(index: Int, week: Int) = editRow(index) {
        val weeks = it.customWeeks.toMutableSet()
        if (!weeks.add(week)) weeks.remove(week)
        it.copy(customWeeks = weeks, weekPattern = WeekPattern.CUSTOM, weeksConflict = false)
    }

    fun updateLocation(index: Int, value: String) = editRow(index) { it.copy(location = value) }

    fun updateTeacher(index: Int, value: String) = editRow(index) { it.copy(teacher = value) }

    // ── 导入 / 丢弃（均二次确认，§5.5） ──────────────────────────────

    fun requestImport() {
        if (!_state.value.canImport) return
        _state.update { it.copy(showImportConfirm = true) }
    }

    fun dismissImportConfirm() = _state.update { it.copy(showImportConfirm = false) }

    /** 确认弹窗点「确认导入」：整批写入 → 删草稿 → 跳周课表。 */
    fun confirmImport() {
        val s = _state.value
        if (!s.canImport) return
        _state.update { it.copy(showImportConfirm = false) }
        viewModelScope.launch {
            val courses = mutableListOf<Course>()
            for (row in s.rows) {
                courses += draftCourse(row) ?: return@launch // 防御：canImport 已拦截
            }
            repository.addCourses(courses)
            repository.deleteDraft(draftId)
            _state.update { it.copy(importedCount = courses.size) }
        }
    }

    fun requestDiscard() = _state.update { it.copy(showDiscardConfirm = true) }

    fun dismissDiscardConfirm() = _state.update { it.copy(showDiscardConfirm = false) }

    /** 全部丢弃 → 删除草稿，不写库（§9.5）。 */
    fun confirmDiscard() {
        viewModelScope.launch {
            repository.deleteDraft(draftId)
            _state.update { it.copy(showDiscardConfirm = false, discarded = true) }
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────────

    private fun editRow(index: Int, transform: (RowState) -> RowState) {
        _state.update { s ->
            val rows = s.rows.toMutableList()
            if (index >= rows.size) return@update s
            rows[index] = transform(rows[index])
            s.copy(rows = rows)
        }
        refreshConflicts()
    }

    /** 草稿行 → 可编辑行（时间模式跟随学期全局，节次映射失败且有时刻则回退时刻）。 */
    private fun toRowState(
        row: com.zjh.timetable.network.RecognizedRow,
        semester: SemesterSetting?,
        timetable: List<PeriodTime>,
    ): RowState {
        val course = row.course
        val parsedStart = course.startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val parsedEnd = course.endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val ps = course.periodStart
        val pe = course.periodEnd
        val periodOk = ps != null && PeriodMapper.periodsToMinutes(ps, pe ?: ps, timetable) != null
        val usePeriod = periodOk && semester?.timetableMode != TimetableMode.EXACT_TIME
        return RowState(
            lowConfidence = row.lowConfidence,
            periodMappingFailed = row.periodMappingFailed,
            weeksConflict = row.weeksConflict,
            weeksRaw = course.weeksRaw,
            name = course.name.orEmpty(),
            dayOfWeek = course.dayOfWeek ?: 1,
            usePeriod = usePeriod,
            startPeriod = ps ?: 1,
            endPeriod = pe ?: ps ?: 1,
            startTime = parsedStart ?: LocalTime.of(8, 0),
            endTime = parsedEnd ?: parsedStart?.plusHours(2) ?: LocalTime.of(9, 40),
            // 节次不可映射且无时刻 → 占位时刻仅用于展示，导入被 missingTime 拦截
            missingTime = !periodOk && parsedStart == null,
            weekPattern = inferWeekPattern(course.weeksParsed),
            customWeeks = course.weeksParsed.toSet(),
            location = course.location.orEmpty(),
            teacher = course.teacher.orEmpty(),
        )
    }

    private fun inferWeekPattern(weeks: List<Int>): WeekPattern = when {
        weeks.isEmpty() -> WeekPattern.ALL
        weeks.all { it % 2 == 1 } -> WeekPattern.ODD
        weeks.all { it % 2 == 0 } -> WeekPattern.EVEN
        else -> WeekPattern.CUSTOM
    }

    /** 重算每行 timeError 与冲突（既有课程 + 行间互撞）。 */
    private fun refreshConflicts() {
        _state.update { s ->
            val bounds = s.rows.map { row -> resolveMinutesQuiet(row) }
            val rows = s.rows.mapIndexed { index, row ->
                val timeError = when {
                    row.usePeriod && bounds[index] == null -> "节次超出作息表，请补全作息或改用时刻"
                    !row.usePeriod && row.missingTime -> "识别缺少时间信息，请补全节次或时刻"
                    !row.usePeriod && row.endTime <= row.startTime -> "结束时间需晚于开始时间"
                    else -> null
                }
                val conflictWith = if (bounds[index] == null) {
                    emptyList()
                } else {
                    val (start, end) = bounds[index]!!
                    val day = row.dayOfWeek
                    val vsExisting = existingCourses
                        .filter { it.dayOfWeek == day && it.startMinute < end && start < it.endMinute }
                        .map { it.name }
                    val vsRows = s.rows.mapIndexedNotNull { other, otherRow ->
                        if (other == index || otherRow.dayOfWeek != day) return@mapIndexedNotNull null
                        val ob = bounds[other] ?: return@mapIndexedNotNull null
                        if (otherRow.name.isBlank()) null
                        else if (ob.first < end && start < ob.second) otherRow.name else null
                    }
                    (vsExisting + vsRows).distinct()
                }
                row.copy(timeError = timeError, conflictWith = conflictWith)
            }
            s.copy(rows = rows)
        }
    }

    private fun resolveMinutesQuiet(row: RowState): Pair<Int, Int>? = if (row.usePeriod) {
        PeriodMapper.periodsToMinutes(row.startPeriod, row.endPeriod, timetable)
    } else {
        val start = row.startTime.hour * 60 + row.startTime.minute
        val end = row.endTime.hour * 60 + row.endTime.minute
        if (end <= start) null else start to end
    }

    /** 编辑行 → Course；时间不可解析返回 null（canImport 已拦截，防御性兜底）。 */
    private fun draftCourse(row: RowState): Course? {
        val bounds = resolveMinutesQuiet(row) ?: return null
        val now = java.time.LocalDateTime.now()
        return Course(
            id = 0,
            name = row.name.trim(),
            dayOfWeek = row.dayOfWeek,
            startMinute = bounds.first,
            endMinute = bounds.second,
            location = row.location.trim().ifEmpty { null },
            teacher = row.teacher.trim().ifEmpty { null },
            weekPattern = row.weekPattern,
            customWeeks = if (row.weekPattern == WeekPattern.CUSTOM) {
                CustomWeeks.encode(row.customWeeks).ifEmpty { null }
            } else {
                null
            },
            createdAt = now,
            updatedAt = now,
        )
    }
}
