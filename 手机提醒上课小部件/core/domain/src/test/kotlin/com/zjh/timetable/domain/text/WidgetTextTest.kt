package com.zjh.timetable.domain.text

import com.zjh.timetable.domain.testCourse
import com.zjh.timetable.domain.testSettings
import com.zjh.timetable.domain.text.WidgetText.Model
import com.zjh.timetable.domain.model.WeekPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 详细设计 §4.7 文案规则：今日无课 / 今日课全结束 / 下一节（§4.4 文案复用 + 倒计时格式）。
 * now 默认取第 2 周周一 2026-03-09 12:00。
 */
class WidgetTextTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 9, 12, 0)

    private fun text(
        vararg courses: com.zjh.timetable.domain.model.Course,
        settings: com.zjh.timetable.domain.model.SemesterSetting = testSettings(),
    ): Model = WidgetText.forToday(courses.toList(), settings, now)

    @Test
    fun `无任何课程显示今天没有课`() {
        assertEquals(Model.NoClassToday, text())
    }

    @Test
    fun `今日不是课日显示今天没有课`() {
        assertEquals(Model.NoClassToday, text(testCourse(dayOfWeek = 2)))
    }

    @Test
    fun `学期外假期显示今天没有课`() {
        val result = text(
            testCourse(),
            settings = testSettings(firstWeekStartDate = LocalDate.of(2026, 9, 7)),
        )
        assertEquals(Model.NoClassToday, result)
    }

    @Test
    fun `CUSTOM 周次不含本周显示今天没有课`() {
        assertEquals(
            Model.NoClassToday,
            text(testCourse(weekPattern = WeekPattern.CUSTOM, customWeeks = "1,3")),
        )
    }

    @Test
    fun `今日课程停课显示今天没有课`() {
        assertEquals(Model.NoClassToday, text(testCourse(pausedDates = "2026-03-09")))
    }

    @Test
    fun `今日课程全部结束显示今天课程已结束`() {
        assertEquals(Model.AllEnded, text(testCourse(startMinute = 480, endMinute = 580)))
    }

    @Test
    fun `仅剩进行中的课程空闲文案回退为课程已结束`() {
        // now 分钟 = 720 == endMinute，课程进行中；下一节展示由小组件布局负责（§7.1）
        assertEquals(
            Model.AllEnded,
            text(testCourse(startMinute = 480, endMinute = 720)),
        )
    }

    @Test
    fun `有下一节输出课程信息与倒计时`() {
        val model = text(testCourse(startMinute = 840, endMinute = 940)) // 14:00
        val next = model as Model.NextClass
        assertEquals(1L, next.courseId)
        assertEquals("高等数学", next.name)
        assertEquals("14:00", next.startText)
        assertEquals("高等数学｜14:00 开始｜A101", next.body)
        assertEquals("距上课还有 2 小时 0 分", next.countdown)
    }

    @Test
    fun `倒计时不足 1 小时显示分钟`() {
        val model = WidgetText.forToday(
            listOf(testCourse(startMinute = 840, endMinute = 940)),
            testSettings(),
            LocalDateTime.of(2026, 3, 9, 13, 30),
        )
        assertEquals("距上课还有 30 分钟", (model as Model.NextClass).countdown)
    }

    @Test
    fun `多门今日课程取最早未开始的`() {
        val model = text(
            testCourse(id = 1, startMinute = 840, endMinute = 940), // 14:00
            testCourse(id = 2, startMinute = 960, endMinute = 1060), // 16:00
        )
        assertEquals(1L, (model as Model.NextClass).courseId)
    }

    @Test
    fun `location 为 null 时正文不带教室段`() {
        val model = text(testCourse(startMinute = 840, endMinute = 940, location = null))
        assertEquals("高等数学｜14:00 开始", (model as Model.NextClass).body)
    }

    @Test
    fun `倒计时格式化边界`() {
        assertEquals("0 分钟", WidgetText.countdownText(0))
        assertEquals("59 分钟", WidgetText.countdownText(59))
        assertEquals("1 小时 0 分", WidgetText.countdownText(60))
        assertEquals("1 小时 23 分", WidgetText.countdownText(83))
    }
}
