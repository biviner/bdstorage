package com.zjh.timetable.domain.text

import com.zjh.timetable.domain.calc.CourseStatusResolver
import com.zjh.timetable.domain.calc.WeekCalculator
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.CourseStatus
import com.zjh.timetable.domain.model.SemesterSetting
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 2×2 小组件与首页「下一节课」文案（详细设计 §4.7）。
 * 全部为纯函数：输入 (courses, settings, now)，输出不可变文案模型。
 */
object WidgetText {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 今日无课 */
    const val TEXT_NO_CLASS_TODAY = "今天没有课"

    /** 今日课全结束 */
    const val TEXT_ALL_ENDED = "今天课程已结束"

    sealed interface Model {
        /** 今日无课（含学期外假期、停课/无效周次导致的当日无课） */
        data object NoClassToday : Model

        /** 今日有课但全部已结束。仅剩进行中课程时也回退到此（进行中展示由小组件布局负责，§7.1） */
        data object AllEnded : Model

        /** 有下一节（今日最早未开始的课程），文案复用 §4.4 格式 */
        data class NextClass(
            val courseId: Long,
            val name: String,
            /** "HH:mm" */
            val startText: String,
            val location: String?,
            /** "$name｜${HH:mm} 开始${｜$location}"，与 §4.4 提醒正文同格式 */
            val body: String,
            /** "距上课还有 X 分钟" / "距上课还有 X 小时 Y 分" */
            val countdown: String,
        ) : Model
    }

    fun forToday(courses: List<Course>, settings: SemesterSetting, now: LocalDateTime): Model {
        val week = WeekCalculator.currentWeek(
            settings.firstWeekStartDate, now.toLocalDate(), settings.totalWeeks,
        )
        val todayCourses = courses.filter { course ->
            course.dayOfWeek == now.dayOfWeek.value &&
                WeekCalculator.courseActiveInWeek(course, week) &&
                now.toLocalDate() !in WeekCalculator.pausedDatesOf(course)
        }
        if (todayCourses.isEmpty()) return Model.NoClassToday

        val nowMinute = now.hour * 60 + now.minute
        val next = todayCourses
            .filter { CourseStatusResolver.statusOf(it, nowMinute) == CourseStatus.NotStarted }
            .minByOrNull { it.startMinute } ?: return Model.AllEnded

        val startAt = now.toLocalDate().atTime(startOf(next))
        val minutes = Duration.between(now, startAt).toMinutes()
        return Model.NextClass(
            courseId = next.id,
            name = next.name,
            startText = startAt.format(TIME_FORMAT),
            location = next.location,
            body = buildString {
                append(next.name)
                append('｜').append(startAt.format(TIME_FORMAT)).append(" 开始")
                next.location?.let { append('｜').append(it) }
            },
            countdown = "距上课还有 " + countdownText(minutes),
        )
    }

    /** 倒计时时长：< 1h 显示 "X 分钟"，≥ 1h 显示 "X 小时 Y 分"。 */
    fun countdownText(minutes: Long): String =
        if (minutes < 60) "$minutes 分钟"
        else "${minutes / 60} 小时 ${minutes % 60} 分"

    private fun startOf(course: Course): LocalTime =
        LocalTime.of(course.startMinute / 60, course.startMinute % 60)
}
