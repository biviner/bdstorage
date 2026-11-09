package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PlannedReminder
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.WeekPattern
import com.zjh.timetable.domain.testCourse
import com.zjh.timetable.domain.testSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 详细设计 §4.4 边界用例表：单双周过滤 / CUSTOM 不连续周 / pausedDates 当日不发 /
 * lead 跨午夜 / 课程在窗口边界 / 全局与单课 lead 优先级 / 过期不补发。
 *
 * 时间线：2026-03-02 为第 1 周周一；now 默认取第 2 周周一 2026-03-09 12:00，
 * 窗口 = 3-09 12:00 ..< 3-16 12:00。
 */
class ReminderPlannerTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 9, 12, 0)

    private fun plan(
        vararg courses: Course,
        settings: SemesterSetting = testSettings(),
    ): List<PlannedReminder> =
        ReminderPlanner.plan(courses.toList(), settings, timetable = null, now = now)

    @Test
    fun `基础排程 下周一课程提前默认 15 分钟提醒`() {
        val result = plan(testCourse())
        assertEquals(1, result.size)
        val reminder = result.single()
        assertEquals(LocalDateTime.of(2026, 3, 16, 7, 45), reminder.triggerAt)
        assertEquals(1L, reminder.courseId)
        assertEquals("15分钟后上课", reminder.title)
        assertEquals("高等数学｜08:00 开始｜A101", reminder.body)
    }

    @Test
    fun `过期时刻不产出 当日已开始的课程不补发`() {
        val result = plan(testCourse()) // 3-09 08:00 的课，lead 15 → 触发 07:45 已过
        assertTrue(result.none { it.triggerAt <= now })
        assertTrue(result.none { it.triggerAt.toLocalDate() == LocalDate.of(2026, 3, 9) })
    }

    @Test
    fun `单双周过滤 偶数周课程在本窗口无有效触发`() {
        val odd = testCourse(id = 1, weekPattern = WeekPattern.ODD)
        val even = testCourse(id = 2, weekPattern = WeekPattern.EVEN)
        val oddResult = plan(odd)
        assertEquals(1, oddResult.size) // 窗口内唯一奇数周周一 = 第 3 周 3-16
        assertEquals(LocalDateTime.of(2026, 3, 16, 7, 45), oddResult.single().triggerAt)
        assertTrue(plan(even).isEmpty()) // 第 2 周触发已过期，第 3 周为奇数周
    }

    @Test
    fun `CUSTOM 不连续周按解码集合过滤`() {
        val course = testCourse(weekPattern = WeekPattern.CUSTOM, customWeeks = "1,3")
        val result = plan(course)
        assertEquals(1, result.size) // 第 3 周命中
        assertEquals(LocalDateTime.of(2026, 3, 16, 7, 45), result.single().triggerAt)
    }

    @Test
    fun `pausedDates 当日不发`() {
        val course = testCourse(pausedDates = "2026-03-16")
        assertTrue(plan(course).isEmpty()) // 窗口内仅有的未来周一 3-16 停课
    }

    @Test
    fun `reminderEnabled 为 false 的课程不进计划`() {
        assertTrue(plan(testCourse(reminderEnabled = false)).isEmpty())
    }

    @Test
    fun `lead 为 0 表示准点提醒且文案为现在上课`() {
        val result = plan(testCourse(reminderLeadMinutes = 0))
        assertEquals(1, result.size)
        assertEquals(LocalDateTime.of(2026, 3, 16, 8, 0), result.single().triggerAt)
        assertEquals("现在上课", result.single().title)
    }

    @Test
    fun `lead 为负视作准点`() {
        val result = plan(testCourse(reminderLeadMinutes = -5))
        assertEquals(LocalDateTime.of(2026, 3, 16, 8, 0), result.single().triggerAt)
        assertEquals("现在上课", result.single().title)
    }

    @Test
    fun `单课 lead 优先于全局默认 lead`() {
        val custom = testCourse(id = 1, reminderLeadMinutes = 30)
        val fallback = testCourse(id = 2, startMinute = 600, endMinute = 700, reminderLeadMinutes = null)
        val result = plan(custom, fallback)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 16, 7, 30), // 30 分钟提前
                LocalDateTime.of(2026, 3, 16, 9, 45), // 全局默认 15 分钟提前
            ),
            result.map { it.triggerAt },
        )
        assertEquals("30分钟后上课", result.first().title)
    }

    @Test
    fun `lead 跨午夜 落在课程前一日的晚上仍纳入计划`() {
        val course = testCourse(dayOfWeek = 2, startMinute = 30, endMinute = 100, reminderLeadMinutes = 60)
        val result = plan(course) // 周二 00:30 的课提前 60 分钟 → 周一 23:30
        assertEquals(1, result.size)
        assertEquals(LocalDateTime.of(2026, 3, 9, 23, 30), result.single().triggerAt)
    }

    @Test
    fun `窗口边界 触发时刻恰等于 now 不收`() {
        val atNow = LocalDateTime.of(2026, 3, 9, 7, 45)
        val result = ReminderPlanner.plan(
            listOf(testCourse()),
            testSettings(),
            timetable = null,
            now = atNow,
        )
        assertTrue(result.isEmpty()) // 当日触发 == now；下周一触发 == 窗口右端
    }

    @Test
    fun `窗口边界 触发时刻恰等于 now 加 7 天不收`() {
        val result = plan(testCourse(reminderLeadMinutes = 0)) // 3-16 08:00 < 3-16 12:00 在窗口内
        assertEquals(1, result.size)
        val atStart = LocalDateTime.of(2026, 3, 9, 8, 0)
        val boundary = ReminderPlanner.plan(
            listOf(testCourse(id = 9, startMinute = 480, endMinute = 580)),
            testSettings(),
            timetable = null,
            now = atStart,
        )
        // 下周一 08:00 触发 = 3-16 08:00 = now+7d 整点，落在半开窗口之外
        assertTrue(boundary.none { it.triggerAt == atStart.plusDays(7) })
    }

    @Test
    fun `输出按 triggerAt 升序`() {
        val monday = testCourse(id = 1, dayOfWeek = 1)
        val tuesday = testCourse(id = 2, dayOfWeek = 2)
        val result = plan(monday, tuesday)
        assertEquals(2, result.size)
        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 10, 7, 45), LocalDateTime.of(2026, 3, 16, 7, 45)),
            result.map { it.triggerAt },
        )
    }

    @Test
    fun `学期外 firstWeekStart 在未来不产出任何提醒`() {
        val result = plan(
            testCourse(),
            settings = testSettings(firstWeekStartDate = LocalDate.of(2026, 9, 7)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `无课列表返回空计划`() {
        assertTrue(plan().isEmpty())
    }
}
