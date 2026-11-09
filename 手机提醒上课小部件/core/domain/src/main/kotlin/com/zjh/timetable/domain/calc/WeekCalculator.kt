package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.Course
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 教学周计算（详细设计 §4.1）。周一为一周起点，第 1 周周一 = firstWeekStart。
 */
object WeekCalculator {

    /**
     * 今天是学期第几周；firstWeekStart 为第 1 周周一。
     * today 早于它、或超过 totalWeeks（today ≥ 起点 + totalWeeks*7 天，即学期结束次日及以后）→ null。
     * week = ((today - firstWeekStart).days / 7) + 1。
     */
    fun currentWeek(firstWeekStart: LocalDate, today: LocalDate, totalWeeks: Int): Int? {
        val days = ChronoUnit.DAYS.between(firstWeekStart, today)
        if (days < 0) return null
        val week = (days / 7).toInt() + 1
        return if (week <= totalWeeks) week else null
    }

    /** [date] 相对学期起点的星期序号，1=周一 .. 7=周日；早于起点的日期按上一周期回推。 */
    fun dayOfWeekOf(firstWeekStart: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(firstWeekStart, date)
        return Math.floorMod(days, 7L).toInt() + 1
    }

    /**
     * 课程在第 [week] 教学周是否上课（详细设计 §4.2 课程级入口）。
     * week 为 null（学期外）→ false；CUSTOM 周次集合由 [CustomWeeks] 解码。
     * 停课日（pausedDates）为日期级过滤，见 [pausedDatesOf] 与 ReminderPlanner。
     */
    fun courseActiveInWeek(course: Course, week: Int?): Boolean {
        if (week == null || week < 1) return false
        val custom = CustomWeeks.decode(course.customWeeks).first
        return course.weekPattern.matches(week, custom)
    }

    /** 解析课程停课日期串 "2026-11-20,2026-11-27" → 日期集合；null/空 → 空集合，非法日期忽略。 */
    fun pausedDatesOf(course: Course): Set<LocalDate> =
        course.pausedDates
            ?.split(',')
            ?.mapNotNull { token ->
                val piece = token.trim()
                if (piece.isEmpty()) null else runCatching { LocalDate.parse(piece) }.getOrNull()
            }
            ?.toSet()
            ?: emptySet()
}
