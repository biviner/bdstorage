package com.zjh.timetable.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjh.timetable.data.local.SchemaConstraints
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.TimetableMode
import com.zjh.timetable.domain.model.WeekPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repository 门面测试（详细设计 §3.3、§3.4；概要设计 §10）。
 * 覆盖：写后 ChangeNotifier 钩子触发（逐个写方法断言）、domain 模型映射、
 * 批量插入单事务、读路径排序。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimetableRepositoryTest {

    private lateinit var db: TimetableDatabase
    private lateinit var repo: TimetableRepository
    private lateinit var notifierCount: AtomicInteger

    private val recordingNotifier = object : ChangeNotifier {
        override suspend fun onTimetableChanged() {
            notifierCount.incrementAndGet()
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TimetableDatabase::class.java,
        ).allowMainThreadQueries()
            .addCallback(SchemaConstraints.callback) // 与生产装配一致，CHECK 触发器生效
            .build()
        notifierCount = AtomicInteger(0)
        repo = TimetableRepository(db, recordingNotifier)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun course(
        name: String = "数据结构",
        dayOfWeek: Int = 2,
        startMinute: Int = 480,
        endMinute: Int = 580,
        weekPattern: WeekPattern = WeekPattern.ALL,
        customWeeks: String? = null,
        pausedDates: String? = null,
    ) = Course(
        id = 0,
        name = name,
        dayOfWeek = dayOfWeek,
        startMinute = startMinute,
        endMinute = endMinute,
        location = "教三-401",
        teacher = "张老师",
        weekPattern = weekPattern,
        customWeeks = customWeeks,
        pausedDates = pausedDates,
        createdAt = LocalDateTime.of(2026, 11, 7, 12, 0),
        updatedAt = LocalDateTime.of(2026, 11, 7, 12, 0),
    )

    private fun assertNotifierCalled(expectedDelta: Int = 1) {
        assertEquals(expectedDelta, notifierCount.get())
    }

    // ── 课程写路径：每个写方法写后必须触发 ChangeNotifier（详细设计 §3.4） ──

    @Test
    fun addCourse_persistsAndNotifies() = runTest {
        val id = repo.addCourse(course())
        assertTrue(id > 0)
        assertNotifierCalled()

        val loaded = repo.course(id)!!
        assertEquals("数据结构", loaded.name)
        assertEquals("教三-401", loaded.location)
        assertEquals(WeekPattern.ALL, loaded.weekPattern)
        assertTrue(loaded.updatedAt >= loaded.createdAt)
    }

    @Test
    fun addCourses_singleTransactionNotifiesOnce() = runTest {
        val ids = repo.addCourses(listOf(course(name = "A"), course(name = "B"), course(name = "C")))
        assertEquals(3, ids.size)
        assertNotifierCalled() // 批量插入 = 一次钩子，而非三次
        assertEquals(3, repo.observeCourses().first().size)
    }

    @Test
    fun updateCourse_persistsAndNotifies() = runTest {
        val id = repo.addCourse(course())
        val before = repo.course(id)!!

        repo.updateCourse(before.copy(name = "数据结构(下)", weekPattern = WeekPattern.CUSTOM, customWeeks = "1-8,10,12-16"))
        assertNotifierCalled(2)

        val after = repo.course(id)!!
        assertEquals("数据结构(下)", after.name)
        assertEquals(WeekPattern.CUSTOM, after.weekPattern)
        assertEquals("1-8,10,12-16", after.customWeeks)
        assertEquals(before.createdAt, after.createdAt) // createdAt 不被更新盖掉
        assertTrue(after.updatedAt >= before.updatedAt)
    }

    @Test
    fun deleteCourse_removesAndNotifies() = runTest {
        val id = repo.addCourse(course())
        val entity = repo.course(id)!!

        repo.deleteCourse(entity)
        assertNotifierCalled(2)
        assertNull(repo.course(id))
    }

    @Test
    fun setPausedDates_persistsAndNotifies() = runTest {
        val id = repo.addCourse(course())

        repo.setPausedDates(id, "2026-11-20,2026-11-27")
        assertNotifierCalled(2)
        assertEquals("2026-11-20,2026-11-27", repo.course(id)!!.pausedDates)

        repo.setPausedDates(id, null)
        assertNull(repo.course(id)!!.pausedDates)
    }

    // ── 学期/作息写路径同样触发钩子（详细设计 §3.4） ─────────────────────

    @Test
    fun saveSemester_persistsAndNotifies() = runTest {
        assertNull(repo.semester())

        val setting = SemesterSetting(
            firstWeekStartDate = LocalDate.of(2026, 11, 9),
            totalWeeks = 18,
            defaultReminderLeadMinutes = 10,
            timetableMode = TimetableMode.EXACT_TIME,
        )
        repo.saveSemester(setting)
        assertNotifierCalled()

        assertEquals(setting, repo.semester())
        assertEquals(setting, repo.observeSemester().first())
    }

    @Test
    fun replaceTimetable_clearsAndNotifies() = runTest {
        repo.replaceTimetable(
            listOf(
                PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(9, 40)),
                PeriodTime(5, LocalTime.of(14, 0), LocalTime.of(15, 40)),
            ),
        )
        assertNotifierCalled()
        assertEquals(listOf(1, 5), repo.timetable().map { it.periodIndex })

        repo.replaceTimetable(listOf(PeriodTime(3, LocalTime.of(10, 0), LocalTime.of(11, 40))))
        assertNotifierCalled(2)
        assertEquals(listOf(3), repo.timetable().map { it.periodIndex })
    }

    // ── 读路径：排序与 domain 映射 ─────────────────────────────────────

    @Test
    fun observeCourses_ordersByDayThenStartMinute() = runTest {
        repo.addCourses(
            listOf(
                course(name = "晚", dayOfWeek = 3, startMinute = 1140),
                course(name = "午", dayOfWeek = 1, startMinute = 840),
                course(name = "早", dayOfWeek = 1, startMinute = 480),
            ),
        )
        assertEquals(listOf("早", "午", "晚"), repo.observeCourses().first().map { it.name })
        assertEquals(listOf("早", "午"), repo.coursesOnDay(1).map { it.name })
    }

    @Test
    fun coursesOnDay_returnsEmptyForUnknownDay() = runTest {
        repo.addCourse(course())
        assertTrue(repo.coursesOnDay(7).isEmpty())
    }

    // ── 识别草稿（不走 ChangeNotifier：草稿非课表权威数据） ─────────────

    @Test
    fun draft_saveReadDelete_doesNotNotify() = runTest {
        val draftId = repo.saveDraft(coursesJson = "[]", rawImageUri = "content://img/1")
        assertTrue(draftId > 0)
        assertNotifierCalled(0) // 草稿写入不触发课程钩子

        val draft = repo.draft(draftId)!!
        assertEquals("[]", draft.coursesJson)
        assertEquals("content://img/1", draft.rawImageUri)

        repo.deleteDraft(draftId)
        assertNull(repo.draft(draftId))
    }

    // ── 钩子契约本身：实现被原样调用 ───────────────────────────────────

    @Test
    fun notifier_receivesExactlyOneCallbackPerWrite() = runTest {
        val id = repo.addCourse(course())
        val loaded = repo.course(id)!!
        repo.updateCourse(loaded)
        repo.deleteCourse(loaded)
        assertEquals(3, notifierCount.get())
    }

    @Test
    fun defaultCourseFields_surviveRoundTrip() = runTest {
        val minimal = Course(
            id = 0,
            name = "体育",
            dayOfWeek = 7,
            startMinute = 1140,
            endMinute = 1200,
            createdAt = LocalDateTime.of(2026, 11, 7, 12, 0),
            updatedAt = LocalDateTime.of(2026, 11, 7, 12, 0),
        )
        val id = repo.addCourse(minimal)
        val loaded = repo.course(id)!!
        assertNull(loaded.location)
        assertNull(loaded.teacher)
        assertNull(loaded.colorArgb)
        assertNull(loaded.note)
        assertNull(loaded.customWeeks)
        assertNull(loaded.reminderLeadMinutes)
        assertNull(loaded.pausedDates)
        assertTrue(loaded.reminderEnabled)
    }
}
