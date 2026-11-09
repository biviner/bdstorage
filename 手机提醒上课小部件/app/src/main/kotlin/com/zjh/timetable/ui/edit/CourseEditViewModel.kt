package com.zjh.timetable.ui.edit

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
import com.zjh.timetable.ui.data.UiTimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

/**
 * 课程编辑（详细设计 §8.4）：字段校验、周次选择、保存前冲突检测（可强制保存）、删除。
 * courseId = -1 表示新建；表单时间模式随学期全局模式默认，可单课切换。
 */
class CourseEditViewModel(
    private val repository: UiTimetableRepository,
    private val courseId: Long,
) : ViewModel() {

    companion object {
        const val NEW_COURSE_ID = -1L

        /** 单课提醒提前量选项（分钟，0=准点；详细设计 §8.4）。 */
        val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30)
    }

    data class UiState(
        val loading: Boolean = true,
        val isNew: Boolean = true,
        val semester: SemesterSetting? = null,
        val timetable: List<PeriodTime> = emptyList(),
        /** 全量课程，用于保存前冲突检测（排除自身） */
        val courses: List<Course> = emptyList(),
        // ── 表单字段 ──
        val name: String = "",
        val dayOfWeek: Int = 1,
        /** true=节次选择器，false=时刻选择器 */
        val usePeriod: Boolean = true,
        val startPeriod: Int = 1,
        val endPeriod: Int = 1,
        val startTime: LocalTime = LocalTime.of(8, 0),
        val endTime: LocalTime = LocalTime.of(9, 40),
        val weekPattern: WeekPattern = WeekPattern.ALL,
        val customWeeks: Set<Int> = emptySet(),
        val location: String = "",
        val teacher: String = "",
        val note: String = "",
        /** null = 调色板自动 */
        val colorArgb: Int? = null,
        val reminderEnabled: Boolean = true,
        /** null = 跟随全局默认；0 = 准点 */
        val reminderLeadMinutes: Int? = null,
        val pausedDates: Set<LocalDate> = emptySet(),
        // ── 校验与弹窗态 ──
        val fieldError: String? = null,
        /** 保存时检出的冲突课程名；非空 → UI 弹「仍要保存？」 */
        val pendingConflicts: List<String> = emptyList(),
        /** 删除确认弹窗（仅已有课程） */
        val showDeleteConfirm: Boolean = false,
        /** 保存/删除完成 → UI 返回 */
        val done: Boolean = false,
    ) {
        /** 保存钮可用性：名称必填（详细设计 §8.4）。 */
        val canSave: Boolean get() = name.isNotBlank()

        /** 周次摘要文案。 */
        val weekSummary: String
            get() = when (weekPattern) {
                WeekPattern.ALL -> "每周"
                WeekPattern.ODD -> "单周"
                WeekPattern.EVEN -> "双周"
                WeekPattern.CUSTOM -> "自定义：" + CustomWeeks.encode(customWeeks).ifBlank { "未选" }
            }
    }

    private val _state = MutableStateFlow(UiState(isNew = courseId == NEW_COURSE_ID))
    val state: StateFlow<UiState> = _state

    private var original: Course? = null

    init {
        viewModelScope.launch {
            // 学期/作息/已有课程合并加载；课程详情就绪前 loading
            val initial = combine(
                repository.observeSemester(),
                repository.observeTimetable(),
                repository.observeCourses(),
            ) { semester, timetable, courses ->
                Triple(semester, timetable, courses)
            }.first()
            val (semester, timetable, courses) = initial
            val existing = if (courseId == NEW_COURSE_ID) null else repository.course(courseId)
            original = existing
            _state.update {
                if (existing != null) {
                    // 已有课程回填：按存储分钟数反推编辑态
                    val periodBounds = minuteToPeriod(existing.startMinute, existing.endMinute, timetable)
                    it.copy(
                        loading = false,
                        isNew = false,
                        semester = semester,
                        timetable = timetable,
                        courses = courses,
                        name = existing.name,
                        dayOfWeek = existing.dayOfWeek,
                        usePeriod = periodBounds != null,
                        startPeriod = periodBounds?.first ?: 1,
                        endPeriod = periodBounds?.second ?: 1,
                        startTime = toTime(existing.startMinute),
                        endTime = toTime(existing.endMinute),
                        weekPattern = existing.weekPattern,
                        customWeeks = CustomWeeks.decode(existing.customWeeks).first,
                        location = existing.location.orEmpty(),
                        teacher = existing.teacher.orEmpty(),
                        note = existing.note.orEmpty(),
                        colorArgb = existing.colorArgb,
                        reminderEnabled = existing.reminderEnabled,
                        reminderLeadMinutes = existing.reminderLeadMinutes,
                        pausedDates = WeekCalculator.pausedDatesOf(existing),
                    )
                } else {
                    it.copy(
                        loading = false,
                        isNew = true,
                        semester = semester,
                        timetable = timetable,
                        courses = courses,
                        usePeriod = semester?.timetableMode != TimetableMode.EXACT_TIME,
                        reminderLeadMinutes = null,
                    )
                }
            }
        }
    }

    // ── 字段更新 ──────────────────────────────────────────────────────

    fun updateName(value: String) = _state.update { it.copy(name = value, fieldError = null) }

    fun updateDay(day: Int) = _state.update { it.copy(dayOfWeek = day) }

    fun updateUsePeriod(usePeriod: Boolean) = _state.update { it.copy(usePeriod = usePeriod, fieldError = null) }

    fun updatePeriods(start: Int, end: Int) = _state.update { it.copy(startPeriod = start, endPeriod = end, fieldError = null) }

    fun updateTimes(start: LocalTime, end: LocalTime) = _state.update { it.copy(startTime = start, endTime = end, fieldError = null) }

    fun updateWeekPattern(pattern: WeekPattern) = _state.update { it.copy(weekPattern = pattern) }

    /** 勾选/取消单个周次（自动切到 CUSTOM）。 */
    fun toggleCustomWeek(week: Int) = _state.update {
        val weeks = it.customWeeks.toMutableSet()
        if (!weeks.add(week)) weeks.remove(week)
        it.copy(customWeeks = weeks, weekPattern = WeekPattern.CUSTOM)
    }

    fun updateLocation(value: String) = _state.update { it.copy(location = value) }

    fun updateTeacher(value: String) = _state.update { it.copy(teacher = value) }

    fun updateNote(value: String) = _state.update { it.copy(note = value) }

    fun updateColor(colorArgb: Int?) = _state.update { it.copy(colorArgb = colorArgb) }

    fun updateReminderEnabled(enabled: Boolean) = _state.update { it.copy(reminderEnabled = enabled) }

    fun updateReminderLead(minutes: Int?) = _state.update { it.copy(reminderLeadMinutes = minutes) }

    fun addPausedDate(date: LocalDate) = _state.update { it.copy(pausedDates = it.pausedDates + date) }

    fun removePausedDate(date: LocalDate) = _state.update { it.copy(pausedDates = it.pausedDates - date) }

    // ── 保存（冲突弹窗流程） ─────────────────────────────────────────

    /** 校验并尝试保存；与既有课程时间冲突时置 [UiState.pendingConflicts] 由 UI 弹窗确认。 */
    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val bounds = resolveMinutes(s)
        if (bounds == null) return // resolveMinutes 已写 fieldError
        val (startMinute, endMinute) = bounds

        val candidate = draftCourse(s, startMinute, endMinute)
        val conflicts = conflictNames(candidate, s)
        if (conflicts.isNotEmpty()) {
            _state.update { it.copy(pendingConflicts = conflicts) }
        } else {
            persist(candidate, s)
        }
    }

    /** 冲突弹窗里选择「仍要保存」。 */
    fun confirmSaveAnyway() {
        val s = _state.value
        _state.update { it.copy(pendingConflicts = emptyList()) }
        val bounds = resolveMinutes(s) ?: return
        persist(draftCourse(s, bounds.first, bounds.second), s)
    }

    fun dismissConflict() = _state.update { it.copy(pendingConflicts = emptyList()) }

    // ── 删除 ─────────────────────────────────────────────────────────

    fun requestDelete() = _state.update { it.copy(showDeleteConfirm = true) }

    fun dismissDelete() = _state.update { it.copy(showDeleteConfirm = false) }

    fun confirmDelete() {
        val existing = original ?: return
        viewModelScope.launch {
            repository.deleteCourse(existing)
            _state.update { it.copy(showDeleteConfirm = false, done = true) }
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────────

    /** 表单 → 当日分钟数；非法时写 [UiState.fieldError] 并返回 null。 */
    private fun resolveMinutes(s: UiState): Pair<Int, Int>? = if (s.usePeriod) {
        PeriodMapper.periodsToMinutes(s.startPeriod, s.endPeriod, s.timetable) ?: run {
            _state.update {
                it.copy(fieldError = "节次超出作息表，请到设置页补全作息时间")
            }
            null
        }
    } else {
        val start = s.startTime.hour * 60 + s.startTime.minute
        val end = s.endTime.hour * 60 + s.endTime.minute
        if (end <= start) {
            _state.update { it.copy(fieldError = "结束时间需晚于开始时间") }
            null
        } else {
            start to end
        }
    }

    private fun draftCourse(s: UiState, startMinute: Int, endMinute: Int): Course {
        val existing = original
        val now = LocalDateTime.now()
        return Course(
            id = existing?.id ?: 0,
            name = s.name.trim(),
            dayOfWeek = s.dayOfWeek,
            startMinute = startMinute,
            endMinute = endMinute,
            location = s.location.trim().ifEmpty { null },
            teacher = s.teacher.trim().ifEmpty { null },
            colorArgb = s.colorArgb,
            note = s.note.trim().ifEmpty { null },
            weekPattern = s.weekPattern,
            customWeeks = if (s.weekPattern == WeekPattern.CUSTOM) CustomWeeks.encode(s.customWeeks).ifEmpty { null } else null,
            reminderEnabled = s.reminderEnabled,
            reminderLeadMinutes = s.reminderLeadMinutes,
            pausedDates = s.pausedDates.joinToString(","),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun persist(course: Course, s: UiState) {
        viewModelScope.launch {
            if (s.isNew) repository.addCourse(course) else repository.updateCourse(course)
            // 停课日期独立写路径（详细设计 §3.3：仅更新 paused 列）；Fake 上随实体走
            _state.update { it.copy(done = true) }
        }
    }

    /** 与既有课程（排除自身）同日且区间重叠者，返回课程名列表（详细设计 §4.6）。 */
    private fun conflictNames(candidate: Course, s: UiState): List<String> =
        s.courses
            .asSequence()
            .filter { it.id != candidate.id }
            .filter {
                it.dayOfWeek == candidate.dayOfWeek &&
                    it.startMinute < candidate.endMinute &&
                    candidate.startMinute < it.endMinute
            }
            .map { it.name }
            .toList()

    private fun toTime(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)

    /** 分钟数反推节次区间：起止分钟均命中某行起点/终点时按节次课编辑，否则回退时刻选择器。 */
    private fun minuteToPeriod(start: Int, end: Int, timetable: List<PeriodTime>): Pair<Int, Int>? {
        val byStart = timetable.firstOrNull { it.startTime.hour * 60 + it.startTime.minute == start } ?: return null
        val byEnd = timetable.firstOrNull { it.endTime.hour * 60 + it.endTime.minute == end } ?: return null
        return byStart.periodIndex to byEnd.periodIndex
    }
}
