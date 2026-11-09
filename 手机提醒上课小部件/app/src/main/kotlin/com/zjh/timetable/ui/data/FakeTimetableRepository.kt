package com.zjh.timetable.ui.data

import com.zjh.timetable.data.repository.RecognitionDraft
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.WeekPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 内存版假仓储（B2-2 UI 车道先行）：StateFlow + 互斥单协程写，语义与真实现一致。
 * 总控换装 core/data 的 `TimetableRepository` 后此类仅留作测试/预览用途。
 */
class FakeTimetableRepository : UiTimetableRepository {

    private val mutex = Mutex()

    private val coursesFlow = MutableStateFlow(seedCourses())

    private val semesterFlow = MutableStateFlow<SemesterSetting?>(
        SemesterSetting(firstWeekStartDate = LocalDate.now().with(DayOfWeek.MONDAY)),
    )

    private val timetableFlow = MutableStateFlow(defaultTimetable())

    override fun observeCourses(): Flow<List<Course>> = coursesFlow

    override suspend fun course(id: Long): Course? =
        coursesFlow.value.firstOrNull { it.id == id }

    override suspend fun addCourse(course: Course): Long = mutex.withLock {
        val newId = nextId++
        coursesFlow.update { it + course.copy(id = newId) }
        newId
    }

    override suspend fun updateCourse(course: Course) = mutex.withLock {
        coursesFlow.update { list -> list.map { if (it.id == course.id) course else it } }
        Unit
    }

    override suspend fun addCourses(courses: List<Course>): List<Long> = mutex.withLock {
        courses.map { course ->
            val newId = nextId++
            coursesFlow.update { it + course.copy(id = newId) }
            newId
        }
    }

    override suspend fun deleteCourse(course: Course) = mutex.withLock {
        coursesFlow.update { list -> list.filterNot { it.id == course.id } }
        Unit
    }

    override fun observeSemester(): Flow<SemesterSetting?> = semesterFlow

    override suspend fun semester(): SemesterSetting? = semesterFlow.value

    override suspend fun saveSemester(setting: SemesterSetting) {
        semesterFlow.value = setting
    }

    override fun observeTimetable(): Flow<List<PeriodTime>> = timetableFlow

    override suspend fun timetable(): List<PeriodTime> = timetableFlow.value

    override suspend fun replaceTimetable(periods: List<PeriodTime>) {
        timetableFlow.value = periods
    }

    // ── 识别草稿（内存版，仅测试/预览） ──────────────────────────────

    private val drafts = mutableMapOf<Long, RecognitionDraft>()
    private var nextDraftId = 1L

    override suspend fun saveDraft(coursesJson: String, rawImageUri: String?): Long {
        val id = nextDraftId++
        drafts[id] = RecognitionDraft(id, LocalDateTime.now(), rawImageUri, coursesJson)
        return id
    }

    override suspend fun draft(id: Long): RecognitionDraft? = drafts[id]

    override suspend fun deleteDraft(id: Long) {
        drafts.remove(id)
    }

    /** 测试/预览自查用：当前草稿 id 集合。 */
    fun draftIds(): List<Long> = drafts.keys.toList()

    private var nextId = seedCourses().maxOf { it.id } + 1

    companion object {
        private var autoId = 0L

        /** 与 core/data DefaultTimetable.seed 相同的默认作息（换装真实现后以真为准）。 */
        fun defaultTimetable(): List<PeriodTime> = listOf(
            PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(9, 40)), // 1-2 节
            PeriodTime(3, LocalTime.of(10, 0), LocalTime.of(11, 40)), // 3-4 节
            PeriodTime(5, LocalTime.of(14, 0), LocalTime.of(15, 40)), // 5-6 节
            PeriodTime(7, LocalTime.of(16, 0), LocalTime.of(17, 40)), // 7-8 节
            PeriodTime(9, LocalTime.of(19, 0), LocalTime.of(20, 40)), // 9-10 节
        )

        /** 演示种子：覆盖 ALL/ODD/CUSTOM 三种周次与一个冲突块，便于走查。 */
        fun seedCourses(): List<Course> {
            val now = java.time.LocalDateTime.now()
            fun course(
                name: String, day: Int, start: Int, end: Int,
                location: String? = null, teacher: String? = null,
                pattern: WeekPattern = WeekPattern.ALL, customWeeks: String? = null,
            ) = Course(
                id = ++autoId, name = name, dayOfWeek = day,
                startMinute = start, endMinute = end,
                location = location, teacher = teacher,
                weekPattern = pattern, customWeeks = customWeeks,
                createdAt = now, updatedAt = now,
            )
            return listOf(
                course("高等数学", 1, 480, 580, "A101", "张老师"),
                course("算法设计", 1, 560, 640, "B202"), // 与高等数学重叠 → ⚠
                course("数据结构", 2, 840, 940, "C303", "李老师", WeekPattern.CUSTOM, "1-8"),
                course("大学英语", 3, 600, 700, "D404"),
                course("体育", 5, 960, 1060, "操场", pattern = WeekPattern.ODD),
            )
        }
    }
}
