package com.zjh.timetable.domain.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 详细设计 §3.2 customWeeks 编解码边界用例表。 */
class CustomWeeksTest {

    // ---- 编码 ----

    @Test
    fun `编码 连续节次合并为区间`() {
        assertEquals("1-8,10,12-16", CustomWeeks.encode(listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 13, 14, 15, 16)))
    }

    @Test
    fun `编码 输入乱序去重后归一化`() {
        assertEquals("1-3,5", CustomWeeks.encode(listOf(5, 3, 1, 2, 5)))
    }

    @Test
    fun `编码 单个周次不加区间`() {
        assertEquals("7", CustomWeeks.encode(listOf(7)))
    }

    @Test
    fun `编码 空集合返回空串`() {
        assertEquals("", CustomWeeks.encode(emptyList()))
    }

    @Test
    fun `编码 解码结果可原样往返`() {
        val weeks = setOf(1, 2, 3, 8, 10, 15, 16, 17)
        assertEquals(weeks, CustomWeeks.decode(CustomWeeks.encode(weeks)).first)
    }

    // ---- 解码 ----

    @Test
    fun `解码 标准串 1-8,10,12-16`() {
        val (weeks, warnings) = CustomWeeks.decode("1-8,10,12-16")
        assertEquals((1..8).toSet() + 10 + (12..16).toSet(), weeks)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `解码 null 与空串返回空集合无警告`() {
        assertEquals(emptySet<Int>() to emptyList<String>(), CustomWeeks.decode(null))
        assertEquals(emptySet<Int>() to emptyList<String>(), CustomWeeks.decode(""))
    }

    @Test
    fun `解码 空段容忍无警告`() {
        val (weeks, warnings) = CustomWeeks.decode(" , 3 , 5,")
        assertEquals(setOf(3, 5), weeks)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `解码 逆序段 16-1 归一化为 1 到 16`() {
        val (weeks, warnings) = CustomWeeks.decode("16-1")
        assertEquals((1..16).toSet(), weeks)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `解码 重复周次去重`() {
        val (weeks, _) = CustomWeeks.decode("5,5,3-4,4")
        assertEquals(setOf(3, 4, 5), weeks)
    }

    @Test
    fun `解码 非法 token 丢弃并附 warning`() {
        val (weeks, warnings) = CustomWeeks.decode("abc,7,1-2-3,单周")
        assertEquals(setOf(7), weeks)
        assertEquals(listOf("abc", "1-2-3", "单周"), warnings)
    }

    @Test
    fun `解码 超界周次保留由 WeekCalculator 过滤`() {
        val (weeks, warnings) = CustomWeeks.decode("1,99")
        assertEquals(setOf(1, 99), weeks)
        assertTrue(warnings.isEmpty())
    }
}
