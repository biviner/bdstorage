package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.FIRST_WEEK_START
import com.zjh.timetable.domain.model.WeekPattern
import com.zjh.timetable.domain.testCourse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate

/** 详细设计 §4.1 边界用例表 + §4.2 课程级入口。 */
class WeekCalculatorTest {

    private val start: LocalDate = FIRST_WEEK_START // 第 1 周周一 2026-03-02
    private val totalWeeks = 20

    // ---- currentWeek 边界用例表（§4.1） ----

    @Test
    fun `today 等于学期起点返回第 1 周`() {
        assertEquals(1, WeekCalculator.currentWeek(start, start, totalWeeks))
    }

    @Test
    fun `today 为首周周日返回第 1 周`() {
        assertEquals(1, WeekCalculator.currentWeek(start, start.plusDays(6), totalWeeks))
    }

    @Test
    fun `today 为第二周周一返回第 2 周`() {
        assertEquals(2, WeekCalculator.currentWeek(start, start.plusDays(7), totalWeeks))
    }

    @Test
    fun `today 早于学期起点返回 null`() {
        assertNull(WeekCalculator.currentWeek(start, start.minusDays(1), totalWeeks))
    }

    @Test
    fun `today 为学期最后一天返回 totalWeeks`() {
        assertEquals(totalWeeks, WeekCalculator.currentWeek(start, start.plusDays(totalWeeks * 7L - 1), totalWeeks))
    }

    @Test
    fun `today 恰为学期结束次日返回 null`() {
        assertNull(WeekCalculator.currentWeek(start, start.plusDays(totalWeeks * 7L), totalWeeks))
    }

    // ---- dayOfWeekOf ----

    @Test
    fun `dayOfWeekOf 起点为周一`() {
        assertEquals(1, WeekCalculator.dayOfWeekOf(start, start))
    }

    @Test
    fun `dayOfWeekOf 一周内周一到周日映射 1 到 7`() {
        for (i in 0..6) {
            assertEquals(i + 1, WeekCalculator.dayOfWeekOf(start, start.plusDays(i.toLong())))
        }
    }

    @Test
    fun `dayOfWeekOf 跨第二周回到周一`() {
        assertEquals(1, WeekCalculator.dayOfWeekOf(start, start.plusDays(7)))
    }

    @Test
    fun `dayOfWeekOf 早于起点的日期按上一周回推`() {
        assertEquals(7, WeekCalculator.dayOfWeekOf(start, start.minusDays(1)))
    }

    // ---- courseActiveInWeek（§4.2 课程级入口） ----

    @Test
    fun `week 为 null 学期外不活跃`() {
        assertFalse(WeekCalculator.courseActiveInWeek(testCourse(weekPattern = ALL), null))
    }

    @Test
    fun `week 为 0 或负数不活跃`() {
        assertFalse(WeekCalculator.courseActiveInWeek(testCourse(weekPattern = ALL), 0))
        assertFalse(WeekCalculator.courseActiveInWeek(testCourse(weekPattern = ALL), -1))
    }

    @Test
    fun `ALL 每周都活跃`() {
        assertTrue(WeekCalculator.courseActiveInWeek(testCourse(weekPattern = ALL), 1))
        assertTrue(WeekCalculator.courseActiveInWeek(testCourse(weekPattern = ALL), 20))
    }

    @Test
    fun `ODD 奇数周活跃偶数周不活跃`() {
        val course = testCourse(weekPattern = ODD)
        assertTrue(WeekCalculator.courseActiveInWeek(course, 1))
        assertFalse(WeekCalculator.courseActiveInWeek(course, 2))
        assertTrue(WeekCalculator.courseActiveInWeek(course, 3))
    }

    @Test
    fun `EVEN 偶数周活跃奇数周不活跃`() {
        val course = testCourse(weekPattern = EVEN)
        assertFalse(WeekCalculator.courseActiveInWeek(course, 1))
        assertTrue(WeekCalculator.courseActiveInWeek(course, 2))
    }

    @Test
    fun `CUSTOM 按解码后的周次集合匹配`() {
        val course = testCourse(weekPattern = CUSTOM, customWeeks = "1-8,10,12-16")
        assertTrue(WeekCalculator.courseActiveInWeek(course, 8))
        assertFalse(WeekCalculator.courseActiveInWeek(course, 9))
        assertTrue(WeekCalculator.courseActiveInWeek(course, 10))
        assertTrue(WeekCalculator.courseActiveInWeek(course, 16))
        assertFalse(WeekCalculator.courseActiveInWeek(course, 17))
    }

    @Test
    fun `CUSTOM 串为空时任何周都不活跃`() {
        val course = testCourse(weekPattern = CUSTOM, customWeeks = "")
        assertFalse(WeekCalculator.courseActiveInWeek(course, 1))
    }

    // ---- pausedDatesOf ----

    @Test
    fun `pausedDatesOf 解析逗号分隔日期并容忍空格`() {
        val course = testCourse(pausedDates = "2026-11-20, 2026-11-27")
        assertEquals(
            setOf(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 27)),
            WeekCalculator.pausedDatesOf(course),
        )
    }

    @Test
    fun `pausedDatesOf 为 null 时返回空集合`() {
        assertTrue(WeekCalculator.pausedDatesOf(testCourse()).isEmpty())
    }

    @Test
    fun `pausedDatesOf 忽略非法日期不抛异常`() {
        val course = testCourse(pausedDates = "bad-date,,2026-11-20")
        val dates = assertDoesNotThrow { WeekCalculator.pausedDatesOf(course) }
        assertEquals(setOf(LocalDate.of(2026, 11, 20)), dates)
    }

    // ALL/ODD/EVEN/CUSTOM：等价于 WeekPattern.ALL 等，缩短测试行宽
    private val ALL = WeekPattern.ALL
    private val ODD = WeekPattern.ODD
    private val EVEN = WeekPattern.EVEN
    private val CUSTOM = WeekPattern.CUSTOM
}
