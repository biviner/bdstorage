package com.zjh.timetable.ui.data

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** B2-2：内存假仓储行为与真实现契约对齐（详细设计 §3.3）。 */
class FakeTimetableRepositoryTest {

    private fun course(name: String, day: Int = 1, start: Int = 480, end: Int = 580) = Course(
        id = 0, name = name, dayOfWeek = day, startMinute = start, endMinute = end,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
    )

    @Test
    fun `addCourse assigns incremental id`() = runTest {
        val repo = FakeTimetableRepository()
        val id1 = repo.addCourse(course("高数"))
        val id2 = repo.addCourse(course("英语"))
        assertTrue(id2 > id1)
        assertEquals("英语", repo.course(id2)?.name)
    }

    @Test
    fun `updateCourse replaces by id`() = runTest {
        val repo = FakeTimetableRepository()
        val id = repo.addCourse(course("旧名"))
        val updated = repo.course(id)!!.copy(name = "新名")
        repo.updateCourse(updated)
        assertEquals("新名", repo.course(id)?.name)
    }

    @Test
    fun `deleteCourse removes`() = runTest {
        val repo = FakeTimetableRepository()
        val id = repo.addCourse(course("要删的课"))
        repo.deleteCourse(repo.course(id)!!)
        assertTrue(repo.observeCourses().first().none { it.id == id })
    }

    @Test
    fun `replaceTimetable swaps all rows`() = runTest {
        val repo = FakeTimetableRepository()
        val newRows = listOf(PeriodTime(1, LocalTime.of(9, 0), LocalTime.of(10, 0)))
        repo.replaceTimetable(newRows)
        assertEquals(newRows, repo.observeTimetable().first())
    }

    @Test
    fun `saveSemester persists`() = runTest {
        val repo = FakeTimetableRepository()
        val setting = SemesterSetting(firstWeekStartDate = LocalDate.of(2026, 3, 2), totalWeeks = 18)
        repo.saveSemester(setting)
        assertEquals(setting, repo.semester())
        assertFalse(repo.semester()!!.firstWeekStartDate.isBefore(LocalDate.of(2026, 3, 2)))
    }
}
