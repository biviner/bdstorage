package com.zjh.timetable.widget

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.WeekPattern
import com.zjh.timetable.domain.text.WidgetText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 小组件状态计算单测（详细设计 §7.1/§7.2 状态规则 + §4 边界用例复用）。
 */
class WidgetStateResolverTest {

    // 学期：2026-09-07（周一）起始，20 周 → 2026-11-07 落在第 9 周（周六，61 天后）
    private val settings = SemesterSetting(firstWeekStartDate = LocalDate.of(2026, 9, 7), totalWeeks = 20)
    private val saturday = LocalDateTime.of(2026, 11, 7, 10, 0) // 第 10 周周六，10:00

    private fun course(
        name: String = "高数",
        dayOfWeek: Int = 6,
        startMinute: Int = 9 * 60,
        endMinute: Int = 10 * 60,
        colorArgb: Int? = null,
        pausedDates: String? = null,
        weekPattern: WeekPattern = WeekPattern.ALL,
        customWeeks: String? = null,
    ): Course {
        val now = LocalDateTime.of(2026, 11, 1, 0, 0)
        return Course(
            id = 1L, name = name, dayOfWeek = dayOfWeek,
            startMinute = startMinute, endMinute = endMinute,
            location = "A101", colorArgb = colorArgb, weekPattern = weekPattern,
            customWeeks = customWeeks, pausedDates = pausedDates,
            createdAt = now, updatedAt = now,
        )
    }

    // ── 2×2 §7.1 ─────────────────────────────────────────────────────

    @Test
    fun `进行中课程优先于未开始课程`() {
        val ongoing = course(name = "英语", startMinute = 9 * 60, endMinute = 11 * 60)
        val upcoming = course(name = "物理", startMinute = 13 * 60, endMinute = 14 * 60)
        val state = WidgetStateResolver.nextClass(listOf(upcoming, ongoing), settings, saturday)
        state as WidgetStateResolver.NextClassState.Active
        assertTrue(state.ongoing)
        assertEquals("英语", state.name)
        assertEquals("结束于 11:00", state.timeText)
        assertEquals("距下课还有 1 小时 0 分", state.countdown)
    }

    @Test
    fun `无进行中时取今日最早未开始`() {
        val later = course(name = "物理", startMinute = 13 * 60, endMinute = 14 * 60)
        val earlier = course(name = "化学", startMinute = 10 * 60 + 30, endMinute = 11 * 60 + 30)
        val state = WidgetStateResolver.nextClass(listOf(later, earlier), settings, saturday)
        state as WidgetStateResolver.NextClassState.Active
        assertFalse(state.ongoing)
        assertEquals("化学", state.name)
        assertEquals("10:30", state.timeText)
        assertEquals("距上课还有 30 分钟", state.countdown)
    }

    @Test
    fun `今日课全结束进入空闲态`() {
        val ended = course(startMinute = 8 * 60, endMinute = 9 * 60)
        val state = WidgetStateResolver.nextClass(listOf(ended), settings, saturday)
        assertEquals(WidgetStateResolver.NextClassState.Idle(WidgetText.TEXT_ALL_ENDED), state)
    }

    @Test
    fun `今日无课进入空闲态`() {
        val otherDay = course(dayOfWeek = 1)
        val state = WidgetStateResolver.nextClass(listOf(otherDay), settings, saturday)
        assertEquals(WidgetStateResolver.NextClassState.Idle(WidgetText.TEXT_NO_CLASS_TODAY), state)
    }

    @Test
    fun `停课日课程不计入`() {
        val paused = course(pausedDates = "2026-11-07")
        val state = WidgetStateResolver.nextClass(listOf(paused), settings, saturday)
        assertEquals(WidgetStateResolver.NextClassState.Idle(WidgetText.TEXT_NO_CLASS_TODAY), state)
    }

    @Test
    fun `非有效周次课程不计入`() {
        val outOfWeek = course(weekPattern = WeekPattern.CUSTOM, customWeeks = null)
        // CUSTOM + 空 customWeeks → 不在任何周上课
        val state = WidgetStateResolver.nextClass(listOf(outOfWeek), settings, saturday)
        assertEquals(WidgetStateResolver.NextClassState.Idle(WidgetText.TEXT_NO_CLASS_TODAY), state)
    }

    @Test
    fun `学期未设置按无课处理`() {
        val state = WidgetStateResolver.nextClass(listOf(course()), null, saturday)
        assertEquals(WidgetStateResolver.NextClassState.Idle(WidgetText.TEXT_NO_CLASS_TODAY), state)
    }

    // ── 4×2 §7.2 ─────────────────────────────────────────────────────

    @Test
    fun `头部含日期星期与周次`() {
        val state = WidgetStateResolver.today(listOf(course()), settings, saturday)
        assertEquals("11月7日 周六 · 第9周", state.header)
        assertNull(state.emptyText)
    }

    @Test
    fun `学期外头部显示假期中`() {
        val holiday = LocalDateTime.of(2027, 3, 1, 10, 0)
        val state = WidgetStateResolver.today(emptyList(), settings, holiday)
        assertEquals("假期中", state.header)
        assertEquals("今天没有课，好好休息", state.emptyText)
    }

    @Test
    fun `最多展示5条且按开始时间排序`() {
        val courses = (1..7).map { i ->
            course(name = "课$i", startMinute = 8 * 60 + i * 30, endMinute = 9 * 60 + i * 30)
        }
        val state = WidgetStateResolver.today(courses, settings, saturday)
        assertEquals(5, state.entries.size)
        assertEquals(listOf("课1", "课2", "课3", "课4", "课5"), state.entries.map { it.name })
        assertEquals("08:30-09:30", state.entries[0].timeText)
    }

    @Test
    fun `状态标记_进行中加粗_已结束半透明_下一节高亮`() {
        val ended = course(name = "已结束", startMinute = 8 * 60, endMinute = 9 * 60)
        val ongoing = course(name = "进行中", startMinute = 9 * 60, endMinute = 11 * 60)
        val next = course(name = "下一节", startMinute = 13 * 60, endMinute = 14 * 60)
        val far = course(name = "更晚", startMinute = 15 * 60, endMinute = 16 * 60)
        val state = WidgetStateResolver.today(listOf(far, next, ongoing, ended), settings, saturday)
        val byName = state.entries.associateBy { it.name }
        assertTrue(byName.getValue("已结束").ended)
        assertFalse(byName.getValue("已结束").ongoing)
        assertTrue(byName.getValue("进行中").ongoing)
        assertTrue(byName.getValue("下一节").isNext)
        assertFalse(byName.getValue("更晚").isNext)
        // isNext 只标第一个未开始的
        assertEquals(1, state.entries.count { it.isNext })
    }

    @Test
    fun `颜色显式指定优先_缺省按名hash稳定`() {
        val explicit = course(name = "甲", colorArgb = 0xFF123456.toInt())
        val auto = course(name = "乙")
        val state = WidgetStateResolver.today(listOf(explicit, auto), settings, saturday)
        assertEquals(0xFF123456.toInt(), state.entries[0].colorArgb)
        assertEquals(WidgetCoursePalette.argbFor("乙"), state.entries[1].colorArgb)
        // 同名课两次计算结果稳定
        assertEquals(
            WidgetStateResolver.today(listOf(auto), settings, saturday).entries[0].colorArgb,
            state.entries[1].colorArgb,
        )
    }
}
