package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.testCourse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 详细设计 §4.6 冲突检测：相接不算冲突。 */
class ConflictDetectorTest {

    @Test
    fun `同日区间重叠判冲突`() {
        val a = testCourse(id = 1, startMinute = 480, endMinute = 580)
        val b = testCourse(id = 2, startMinute = 560, endMinute = 660)
        assertEquals(listOf(1L to 2L), ConflictDetector.conflictsOf(listOf(a, b)))
    }

    @Test
    fun `相接不判冲突`() {
        val a = testCourse(id = 1, startMinute = 480, endMinute = 580)
        val b = testCourse(id = 2, startMinute = 580, endMinute = 660)
        assertEquals(emptyList<Pair<Long, Long>>(), ConflictDetector.conflictsOf(listOf(a, b)))
    }

    @Test
    fun `不同日期不判冲突`() {
        val a = testCourse(id = 1, dayOfWeek = 1, startMinute = 480, endMinute = 580)
        val b = testCourse(id = 2, dayOfWeek = 2, startMinute = 480, endMinute = 580)
        assertEquals(emptyList<Pair<Long, Long>>(), ConflictDetector.conflictsOf(listOf(a, b)))
    }

    @Test
    fun `多门课多组冲突按 id 对升序输出`() {
        val a = testCourse(id = 3, startMinute = 480, endMinute = 580)
        val b = testCourse(id = 1, startMinute = 500, endMinute = 600)
        val c = testCourse(id = 2, startMinute = 550, endMinute = 650)
        assertEquals(
            listOf(1L to 2L, 1L to 3L, 2L to 3L),
            ConflictDetector.conflictsOf(listOf(a, b, c)),
        )
    }

    @Test
    fun `空列表与单门课无冲突`() {
        assertEquals(emptyList<Pair<Long, Long>>(), ConflictDetector.conflictsOf(emptyList()))
        assertEquals(emptyList<Pair<Long, Long>>(), ConflictDetector.conflictsOf(listOf(testCourse())))
    }
}
