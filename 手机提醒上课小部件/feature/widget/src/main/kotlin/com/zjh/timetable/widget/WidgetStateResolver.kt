package com.zjh.timetable.widget

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.calc.CourseStatusResolver
import com.zjh.timetable.domain.calc.WeekCalculator
import com.zjh.timetable.domain.model.CourseStatus
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.text.WidgetText
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 小组件状态纯函数计算（详细设计 §7.1/§7.2；概要设计 §6.3）。
 * 输入 (courses, settings, now)，输出不可变状态模型，供 Glance 布局直接渲染；
 * 全部无 Android 依赖，可 JUnit 覆盖。
 */
object WidgetStateResolver {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val WEEKDAY_NAMES = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 2×2 状态（§7.1）：进行中优先 → 最早未开始 → 空闲态文案（§4.7）。 */
    sealed interface NextClassState {
        /** 空闲态：今日无课 / 今日课全结束 */
        data class Idle(val text: String) : NextClassState

        data class Active(
            val courseId: Long,
            /** true=进行中（徽标「进行中」，时间行显示「结束于 HH:mm」） */
            val ongoing: Boolean,
            val name: String,
            /** 未开始 "HH:mm"；进行中 "结束于 HH:mm" */
            val timeText: String,
            val location: String?,
            /** "距上课还有 X 分钟" / "距下课还有 X 分钟"（复用 §4.7 倒计时格式） */
            val countdown: String,
        ) : NextClassState
    }

    /** 4×2 单条目（§7.2）：色块 + 名称 + 时间段 + 教室；进行中加粗徽标、已结束半透明、下一节高亮竖条。 */
    data class TodayEntry(
        val name: String,
        /** "HH:mm-HH:mm" */
        val timeText: String,
        val location: String?,
        /** 已解析颜色：显式 colorArgb 优先，缺省按 name hash 取 8 色调色板 */
        val colorArgb: Int,
        val ongoing: Boolean,
        val ended: Boolean,
        /** 今日第一个未开始的课程（左侧高亮竖条） */
        val isNext: Boolean,
    )

    /** 4×2 状态（§7.2）：头部 + 最多 5 条 + 空态文案。 */
    data class TodayState(
        /** "M月d日 周X · 第N周"；学期外/未设置 → "假期中" */
        val header: String,
        val entries: List<TodayEntry>,
        /** 今日无课时的空态文案；有课为 null */
        val emptyText: String?,
    )

    fun nextClass(courses: List<Course>, settings: SemesterSetting?, now: LocalDateTime): NextClassState {
        val today = coursesOfToday(courses, settings, now)
        if (today.isEmpty()) return NextClassState.Idle(WidgetText.TEXT_NO_CLASS_TODAY)

        val nowMinute = now.hour * 60 + now.minute
        val ongoing = today
            .filter { CourseStatusResolver.statusOf(it, nowMinute) == CourseStatus.Ongoing }
            .minByOrNull { it.startMinute }
        if (ongoing != null) {
            val endMinute = ongoing.endMinute
            return NextClassState.Active(
                courseId = ongoing.id,
                ongoing = true,
                name = ongoing.name,
                timeText = "结束于 " + formatMinute(endMinute),
                location = ongoing.location,
                countdown = "距下课还有 " + WidgetText.countdownText((endMinute - nowMinute).toLong()),
            )
        }

        val next = today
            .filter { CourseStatusResolver.statusOf(it, nowMinute) == CourseStatus.NotStarted }
            .minByOrNull { it.startMinute } ?: return NextClassState.Idle(WidgetText.TEXT_ALL_ENDED)

        val startAt = now.toLocalDate().atTime(startOf(next))
        val minutes = Duration.between(now, startAt).toMinutes()
        return NextClassState.Active(
            courseId = next.id,
            ongoing = false,
            name = next.name,
            timeText = formatMinute(next.startMinute),
            location = next.location,
            countdown = "距上课还有 " + WidgetText.countdownText(minutes),
        )
    }

    fun today(courses: List<Course>, settings: SemesterSetting?, now: LocalDateTime): TodayState {
        val week = currentWeekOf(settings, now)
        val header = if (week == null) "假期中"
        else "${now.monthValue}月${now.dayOfMonth}日 ${WEEKDAY_NAMES[now.dayOfWeek.value]} · 第${week}周"

        val today = coursesOfToday(courses, settings, now).sortedBy { it.startMinute }
        if (today.isEmpty()) return TodayState(header, emptyList(), "今天没有课，好好休息")

        val nowMinute = now.hour * 60 + now.minute
        var nextMarked = false
        val entries = today.take(5).map { course ->
            val status = CourseStatusResolver.statusOf(course, nowMinute)
            val isNext = status == CourseStatus.NotStarted && !nextMarked
            if (isNext) nextMarked = true
            TodayEntry(
                name = course.name,
                timeText = "${formatMinute(course.startMinute)}-${formatMinute(course.endMinute)}",
                location = course.location,
                colorArgb = course.colorArgb ?: WidgetCoursePalette.argbFor(course.name),
                ongoing = status == CourseStatus.Ongoing,
                ended = status == CourseStatus.Ended,
                isNext = isNext,
            )
        }
        return TodayState(header, entries, emptyText = null)
    }

    /** 今日有效课程：dayOfWeek=今天、activeWeek、非停课日（§7.1 过滤规则）。 */
    fun coursesOfToday(courses: List<Course>, settings: SemesterSetting?, now: LocalDateTime): List<Course> {
        val week = currentWeekOf(settings, now)
        val today = now.toLocalDate()
        return courses.filter { course ->
            course.dayOfWeek == now.dayOfWeek.value &&
                WeekCalculator.courseActiveInWeek(course, week) &&
                today !in WeekCalculator.pausedDatesOf(course)
        }
    }

    private fun currentWeekOf(settings: SemesterSetting?, now: LocalDateTime): Int? =
        settings?.let { WeekCalculator.currentWeek(it.firstWeekStartDate, now.toLocalDate(), it.totalWeeks) }

    private fun formatMinute(minuteOfDay: Int): String =
        LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(TIME_FORMAT)

    private fun startOf(course: Course): LocalTime =
        LocalTime.of(course.startMinute / 60, course.startMinute % 60)
}
