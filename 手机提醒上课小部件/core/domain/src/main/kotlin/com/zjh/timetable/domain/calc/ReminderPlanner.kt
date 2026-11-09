package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.PlannedReminder
import com.zjh.timetable.domain.model.SemesterSetting
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 提醒计划（详细设计 §4.4）：纯计算，输出未来 7 天内的提醒触发时刻清单，
 * 调度层（ReminderScheduler）只按清单设置闹钟，不理解业务规则。
 *
 * 窗口：now ..< now+7d。逐课逐日扫描：
 *  1) 该日期的教学周 w（WeekCalculator）
 *  2) courseActiveInWeek 且该日期不在 pausedDates
 *  3) triggerAt = 日期 + 课程开始时间 - lead（lead = 单课优先，null 跟随全局默认，≤0 视为准点）
 *  4) triggerAt > now 才收（过期不补发）
 * 输出按 triggerAt 升序。
 */
object ReminderPlanner {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    const val WINDOW_DAYS = 7L

    /**
     * [timetable] 为作息表；v1 提醒文案直接使用课程起止分钟，该参数保留给后续
     * 节次模式展示扩展（概要 §4.1 契约固定，语义不变）。
     */
    fun plan(
        courses: List<Course>,
        settings: SemesterSetting,
        timetable: List<PeriodTime>? = null,
        now: LocalDateTime,
    ): List<PlannedReminder> {
        val windowEnd = now.plusDays(WINDOW_DAYS)
        val result = mutableListOf<PlannedReminder>()
        for (course in courses) {
            if (!course.reminderEnabled) continue
            val lead = (course.reminderLeadMinutes ?: settings.defaultReminderLeadMinutes)
                .coerceAtLeast(0)
            // 扫描到 now.date + WINDOW_DAYS：lead 跨午夜可能把 now+7d 边界日课程的
            // 触发时刻拉回窗口内（triggerAt < windowEnd 过滤兜底）
            for (offset in 0..WINDOW_DAYS) {
                val date = now.toLocalDate().plusDays(offset)
                val week = WeekCalculator.currentWeek(
                    settings.firstWeekStartDate, date, settings.totalWeeks,
                ) ?: continue
                if (course.dayOfWeek != date.dayOfWeek.value) continue
                if (!WeekCalculator.courseActiveInWeek(course, week)) continue
                if (date in WeekCalculator.pausedDatesOf(course)) continue
                val startAt = date.atTime(
                    LocalTime.of(course.startMinute / 60, course.startMinute % 60),
                )
                val triggerAt = startAt.minusMinutes(lead.toLong())
                if (triggerAt > now && triggerAt < windowEnd) {
                    result += PlannedReminder(
                        courseId = course.id,
                        triggerAt = triggerAt,
                        title = if (lead == 0) "现在上课" else "${lead}分钟后上课",
                        body = buildString {
                            append(course.name)
                            append('｜').append(startAt.format(TIME_FORMAT)).append(" 开始")
                            course.location?.let { append('｜').append(it) }
                        },
                    )
                }
            }
        }
        return result.sortedBy { it.triggerAt }
    }
}
