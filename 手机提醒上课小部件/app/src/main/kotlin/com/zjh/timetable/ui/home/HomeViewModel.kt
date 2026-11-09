package com.zjh.timetable.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zjh.timetable.domain.calc.CourseStatusResolver
import com.zjh.timetable.domain.calc.WeekCalculator
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.CourseStatus
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.text.WidgetText
import com.zjh.timetable.ui.data.UiTimetableRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

/**
 * 首页状态（详细设计 §8.2）：课程/学期两路 Room Flow + 每分钟时钟合并。
 * 下一节课文案复用 [WidgetText]（§4.7，与 2×2 小组件同源）；今日时间线
 * 按 §7.2 布局规则（进行中加粗徽标、已结束半透明、下一节高亮竖条）。
 */
class HomeViewModel(
    repository: UiTimetableRepository,
    /** 时钟流：默认每分钟滴答刷新倒计时；测试注入固定时刻。 */
    nowFlow: Flow<LocalDateTime> = minuteTicker(),
) : ViewModel() {

    /** 今日时间线行：课程 + 当日状态 + 是否下一节。 */
    data class TodayCourse(
        val course: Course,
        val status: CourseStatus,
        val isNext: Boolean,
    )

    data class UiState(
        val loading: Boolean = true,
        val semester: SemesterSetting? = null,
        val courses: List<Course> = emptyList(),
        val now: LocalDateTime = LocalDateTime.now(),
    ) {
        /** 今天所在教学周；学期外为 null。 */
        val currentWeek: Int?
            get() = semester
                ?.let { WeekCalculator.currentWeek(it.firstWeekStartDate, now.toLocalDate(), it.totalWeeks) }

        /** 下一节课卡片文案（学期未设置时按无课处理）。 */
        val nextClass: WidgetText.Model
            get() = semester
                ?.let { WidgetText.forToday(courses, it, now) }
                ?: WidgetText.Model.NoClassToday

        /** 今日有效课程（周次规则 + 停课日过滤，按开始时间排序）。 */
        val todayCourses: List<TodayCourse>
            get() {
                val settings = semester ?: return emptyList()
                val week = WeekCalculator.currentWeek(settings.firstWeekStartDate, now.toLocalDate(), settings.totalWeeks)
                val nowMinute = now.hour * 60 + now.minute
                val list = courses
                    .filter {
                        it.dayOfWeek == now.dayOfWeek.value &&
                            WeekCalculator.courseActiveInWeek(it, week) &&
                            now.toLocalDate() !in WeekCalculator.pausedDatesOf(it)
                    }
                    .sortedBy { it.startMinute }
                val nextId = list
                    .firstOrNull { CourseStatusResolver.statusOf(it, nowMinute) == CourseStatus.NotStarted }
                    ?.id
                return list.map { TodayCourse(it, CourseStatusResolver.statusOf(it, nowMinute), it.id == nextId) }
            }
    }

    val state: StateFlow<UiState> =
        combine(
            repository.observeCourses(),
            repository.observeSemester(),
            nowFlow,
        ) { courses, semester, now ->
            UiState(loading = false, semester = semester, courses = courses, now = now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private companion object {
        /** 每分钟滴答，驱动倒计时/进行中状态刷新。 */
        fun minuteTicker(): Flow<LocalDateTime> = flow {
            while (true) {
                emit(LocalDateTime.now())
                delay(60_000)
            }
        }
    }
}
