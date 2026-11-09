package com.zjh.timetable.network

import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.RecognizedCourse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** §5.2 规则 2-4 纯函数单测（含看板验收反例）。 */
class RecognitionValidatorTest {

    /** 作息表：仅第 1-2 节 */
    private val timetable = listOf(
        PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        PeriodTime(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
    )

    private fun course(
        name: String? = "高等数学",
        dayOfWeek: Int? = 1,
        periodStart: Int? = 1,
        periodEnd: Int? = 2,
        startTime: String? = null,
        endTime: String? = null,
        weeksRaw: String? = "1-16",
        weeksParsed: List<Int> = (1..16).toList(),
        confidence: Double = 0.95,
    ) = RecognizedCourse(
        name = name,
        dayOfWeek = dayOfWeek,
        periodStart = periodStart,
        periodEnd = periodEnd,
        startTime = startTime,
        endTime = endTime,
        weeksRaw = weeksRaw,
        weeksParsed = weeksParsed,
        location = "教1-101",
        teacher = "张三",
        confidence = confidence,
    )

    @Test
    fun `规则2 - 反例 confidence 0_2 且 name 缺失被丢弃并计数`() {
        val outcome = RecognitionValidator.validate(listOf(course(name = null, confidence = 0.2)), timetable)
        assertEquals(0, outcome.rows.size)
        assertEquals(1, outcome.droppedCount)
    }

    @Test
    fun `规则2 - confidence 低于 0_3 但 name 存在也丢弃`() {
        val outcome = RecognitionValidator.validate(listOf(course(confidence = 0.25)), timetable)
        assertEquals(1, outcome.droppedCount)
    }

    @Test
    fun `规则2 - confidence 0_5 保留并标黄`() {
        val outcome = RecognitionValidator.validate(listOf(course(confidence = 0.5)), timetable)
        val row = outcome.rows.single()
        assertEquals(0, outcome.droppedCount)
        assertTrue(row.lowConfidence)
        assertTrue(row.needsReview)
    }

    @Test
    fun `规则2 - 关键字段缺失标黄 dayOfWeek 缺失`() {
        val outcome = RecognitionValidator.validate(listOf(course(dayOfWeek = null)), timetable)
        assertTrue(outcome.rows.single().lowConfidence)
    }

    @Test
    fun `规则2 - 无节次且时间缺失标黄`() {
        val outcome = RecognitionValidator.validate(
            listOf(course(periodStart = null, periodEnd = null, startTime = null, endTime = null)),
            timetable,
        )
        assertTrue(outcome.rows.single().lowConfidence)
    }

    @Test
    fun `规则3 - 节次超出作息表映射失败标黄`() {
        val outcome = RecognitionValidator.validate(listOf(course(periodStart = 5, periodEnd = 6)), timetable)
        val row = outcome.rows.single()
        assertTrue(row.periodMappingFailed)
        assertTrue(row.needsReview)
        assertFalse(row.lowConfidence)
    }

    @Test
    fun `规则3 - 节次在作息表内不标黄`() {
        val outcome = RecognitionValidator.validate(listOf(course()), timetable)
        val row = outcome.rows.single()
        assertFalse(row.periodMappingFailed)
        assertFalse(row.needsReview)
    }

    @Test
    fun `规则4 - 原文含单周而解析含偶数周冲突标黄`() {
        val outcome = RecognitionValidator.validate(
            listOf(course(weeksRaw = "单周", weeksParsed = listOf(2, 4, 6))),
            timetable,
        )
        assertTrue(outcome.rows.single().weeksConflict)
    }

    @Test
    fun `规则4 - 原文含双周而解析含奇数周冲突标黄`() {
        val outcome = RecognitionValidator.validate(
            listOf(course(weeksRaw = "双周", weeksParsed = listOf(1, 3))),
            timetable,
        )
        assertTrue(outcome.rows.single().weeksConflict)
    }

    @Test
    fun `规则4 - 单双相符不标黄`() {
        val outcome = RecognitionValidator.validate(
            listOf(course(weeksRaw = "单周", weeksParsed = listOf(1, 3, 5))),
            timetable,
        )
        assertFalse(outcome.rows.single().weeksConflict)
    }

    @Test
    fun `混合输入 - 丢弃计数与保留行并存`() {
        val outcome = RecognitionValidator.validate(
            listOf(
                course(),
                course(name = "体育", confidence = 0.4),
                course(name = null, confidence = 0.1),
            ),
            timetable,
        )
        assertEquals(1, outcome.droppedCount)
        assertEquals(2, outcome.rows.size)
        assertTrue(outcome.rows[1].lowConfidence)
    }
}
