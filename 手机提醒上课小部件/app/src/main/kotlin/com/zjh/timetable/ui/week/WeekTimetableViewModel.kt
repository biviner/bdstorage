package com.zjh.timetable.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zjh.timetable.domain.calc.WeekCalculator
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.ui.data.UiTimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * 周课表状态（详细设计 §8.3）：学期/作息/课程三路 Flow 合并 + 浏览周次 UI 态。
 * 浏览周次仅内存（详细设计 §8.6：activeWeekOverride 重启失效，DataStore key 由 B4 落地）。
 */
class WeekTimetableViewModel(
    repository: UiTimetableRepository,
    initialWeek: Int = 1,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val semester: SemesterSetting? = null,
        val timetable: List<PeriodTime> = emptyList(),
        val courses: List<Course> = emptyList(),
        /** 正在浏览的教学周（1 起；学期外可为超界值，由 UI 显示「假期中」） */
        val week: Int = 1,
    ) {
        /** 今天所在教学周；学期外为 null。 */
        val currentWeek: Int?
            get() = semester
                ?.let { WeekCalculator.currentWeek(it.firstWeekStartDate, LocalDate.now(), it.totalWeeks) }

        val isCurrentWeek: Boolean get() = week == currentWeek

        /** 浏览周是否在学期内。 */
        val inSemester: Boolean get() = semester != null && week in 1..semester.totalWeeks

        /** 本周有效课程（含周次规则过滤，停课日为日期级不影响周网格）。 */
        val weekCourses: List<Course>
            get() = courses.filter { WeekCalculator.courseActiveInWeek(it, week.takeIf { inSemester }) }
    }

    private val week = MutableStateFlow(initialWeek)

    val state: StateFlow<UiState> =
        combine(
            repository.observeSemester(),
            repository.observeTimetable(),
            repository.observeCourses(),
            week,
        ) { semester, timetable, courses, week ->
            UiState(
                loading = false,
                semester = semester,
                timetable = timetable,
                courses = courses,
                week = week,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** 跳转到指定周（下拉选择）。 */
    fun jumpToWeek(target: Int) {
        week.value = target.coerceAtLeast(1)
    }

    fun previousWeek() {
        week.value = (week.value - 1).coerceAtLeast(1)
    }

    fun nextWeek() {
        week.value += 1
    }

    /** 回到「本周」（无学期设置时回第 1 周）。 */
    fun backToCurrentWeek() {
        week.value = state.value.currentWeek ?: 1
    }

    /** 删除课程走编辑页；此处仅暴露刷新需要的数据源（写路径在 CourseEditViewModel）。 */
    companion object {
        /** 供「下拉跳转」菜单生成 1..totalWeeks 列表；无学期设置时给默认 20。 */
        fun weekOptions(semester: SemesterSetting?): IntRange =
            1..(semester?.totalWeeks ?: 20)
    }
}
