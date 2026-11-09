package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.CourseStatus
import com.zjh.timetable.domain.testCourse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 详细设计 §4.3 边界用例表：now==start、now==end 均算进行中，now==end+1 算已结束。 */
class CourseStatusResolverTest {

    private val start = 480 // 08:00
    private val end = 580 // 09:40

    @Test
    fun `now 早于 start 为未开始`() {
        assertEquals(CourseStatus.NotStarted, CourseStatusResolver.resolve(start, end, start - 1))
    }

    @Test
    fun `now 恰等于 start 算进行中`() {
        assertEquals(CourseStatus.Ongoing, CourseStatusResolver.resolve(start, end, start))
    }

    @Test
    fun `now 在区间中段为进行中`() {
        assertEquals(CourseStatus.Ongoing, CourseStatusResolver.resolve(start, end, 530))
    }

    @Test
    fun `now 恰等于 end 算进行中 含下课铃当分钟`() {
        assertEquals(CourseStatus.Ongoing, CourseStatusResolver.resolve(start, end, end))
    }

    @Test
    fun `now 等于 end 加 1 算已结束`() {
        assertEquals(CourseStatus.Ended, CourseStatusResolver.resolve(start, end, end + 1))
    }

    @Test
    fun `statusOf 按课程字段比较`() {
        val course = testCourse(startMinute = start, endMinute = end)
        assertEquals(CourseStatus.NotStarted, CourseStatusResolver.statusOf(course, 479))
        assertEquals(CourseStatus.Ongoing, CourseStatusResolver.statusOf(course, 480))
        assertEquals(CourseStatus.Ended, CourseStatusResolver.statusOf(course, 581))
    }
}
