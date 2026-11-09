package com.zjh.timetable.scheduler

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjh.timetable.data.local.SchemaConstraints
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.data.repository.TimetableRepository
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.WeekPattern
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 调度器单测（详细设计 §10 scheduler 行：requestCode 稳定性见 [ReminderRequestsTest]、
 * 全局约束 §9.3 幂等重排、§9.4 删除课程后旧闹钟清除）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var db: TimetableDatabase
    private lateinit var repo: TimetableRepository
    private lateinit var scheduler: ReminderSchedulerImpl

    /** 固定「现在」：2026-11-07（周六）10:00。 */
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 7, 10, 0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(SchemaConstraints.callback)
            .build()
        repo = TimetableRepository(db, NoopNotifier)
        scheduler = ReminderSchedulerImpl(
            context,
            repo,
            Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 5 分钟后（10:05 周六）开始的课，准点提醒（lead=0）→ 触发时刻 10:05，在 7 天窗口内。 */
    private suspend fun seedUpcomingCourse(): Course {
        repo.saveSemester(
            // 第 1 周周一 = 本周一（2026-11-02），10:05 落在第 1 周内
            SemesterSetting(firstWeekStartDate = now.toLocalDate().minusDays(5), totalWeeks = 20),
        )
        val course = Course(
            id = 0,
            name = "数据结构",
            dayOfWeek = 6,
            startMinute = 10 * 60 + 5,
            endMinute = 11 * 60 + 5,
            weekPattern = WeekPattern.ALL,
            reminderEnabled = true,
            reminderLeadMinutes = 0,
            createdAt = now,
            updatedAt = now,
        )
        val id = repo.addCourse(course)
        return course.copy(id = id)
    }

    private fun scheduledAlarms() =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    @Test
    fun `重排后按计划设置精确闹钟`() = runTest {
        seedUpcomingCourse()
        scheduler.rescheduleAll()
        val alarms = scheduledAlarms()
        assertEquals(1, alarms.size)
        val expectedTrigger = now.plusMinutes(5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, alarms[0].triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms[0].type)
    }

    @Test
    fun `连续重排幂等（§9_3）`() = runTest {
        seedUpcomingCourse()
        scheduler.rescheduleAll()
        val first = scheduledAlarms().map { it.triggerAtTime }.toSet()
        scheduler.rescheduleAll()
        val second = scheduledAlarms().map { it.triggerAtTime }.toSet()
        assertEquals(first, second)
        assertEquals(1, second.size)
    }

    @Test
    fun `删除课程后旧闹钟被清除`() = runTest {
        val course = seedUpcomingCourse()
        scheduler.rescheduleAll()
        assertEquals(1, scheduledAlarms().size)
        repo.deleteCourse(course)
        scheduler.rescheduleAll()
        assertTrue(scheduledAlarms().isEmpty())
    }

    @Test
    fun `学期未设置只清空不排新`() = runTest {
        scheduler.rescheduleAll()
        assertTrue(scheduledAlarms().isEmpty())
    }
}
