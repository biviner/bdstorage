package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.PeriodTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** 详细设计 §4.5 节次映射。 */
class PeriodMapperTest {

    private val timetable = listOf(
        PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(9, 40)),
        PeriodTime(2, LocalTime.of(10, 0), LocalTime.of(11, 40)),
        PeriodTime(3, LocalTime.of(14, 0), LocalTime.of(15, 40)),
    )

    @Test
    fun `跨节连堂映射为起止分钟`() {
        assertEquals(480 to 700, PeriodMapper.periodsToMinutes(1, 2, timetable))
    }

    @Test
    fun `单节映射`() {
        assertEquals(600 to 700, PeriodMapper.periodsToMinutes(2, 2, timetable))
    }

    @Test
    fun `节次缺失如第 11 节返回 null`() {
        assertNull(PeriodMapper.periodsToMinutes(1, 11, timetable))
    }

    @Test
    fun `结束节次早于开始节次返回 null`() {
        assertNull(PeriodMapper.periodsToMinutes(3, 2, timetable))
    }

    @Test
    fun `空作息表返回 null`() {
        assertNull(PeriodMapper.periodsToMinutes(1, 1, emptyList()))
    }
}
