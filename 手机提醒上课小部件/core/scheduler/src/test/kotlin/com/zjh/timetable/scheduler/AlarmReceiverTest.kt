package com.zjh.timetable.scheduler

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * AlarmReceiver 单测（详细设计 §6.1/§10：陈旧闹钟防陈旧重查——已删除/已停课静默跳过；
 * 有效载荷发通知并滚动补排）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AlarmReceiverTest {

    private lateinit var context: Context
    private lateinit var db: TimetableDatabase
    private lateinit var repo: TimetableRepository
    private lateinit var graph: ReminderGraph
    private val receiver = AlarmReceiver()

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 7, 10, 0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(SchemaConstraints.callback)
            .build()
        repo = TimetableRepository(db, NoopNotifier)
        graph = ReminderGraph.build(context, repo)
        // API 33+ 发通知需要运行时权限，Robolectric 默认不授予
        shadowOf(context as android.app.Application).grantPermissions(
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedCourse(pausedDates: String? = null): Course {
        repo.saveSemester(
            SemesterSetting(firstWeekStartDate = now.toLocalDate().minusDays(5), totalWeeks = 20),
        )
        val course = Course(
            id = 0,
            name = "操作系统",
            dayOfWeek = 6,
            startMinute = 10 * 60 + 5,
            endMinute = 11 * 60 + 5,
            weekPattern = WeekPattern.ALL,
            reminderEnabled = true,
            reminderLeadMinutes = 0,
            pausedDates = pausedDates,
            createdAt = now,
            updatedAt = now,
        )
        return course.copy(id = repo.addCourse(course))
    }

    private fun intent(courseId: Long, triggerAt: LocalDateTime): Intent =
        Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_COURSE_ID, courseId)
            .putExtra(
                AlarmReceiver.EXTRA_TRIGGER_AT_MILLIS,
                triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            .putExtra(AlarmReceiver.EXTRA_TITLE, "现在上课")
            .putExtra(AlarmReceiver.EXTRA_BODY, "操作系统｜10:05 开始")

    private fun postedNotifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun `有效载荷发通知到 class_reminder 渠道并滚动补排`() = runTest {
        val course = seedCourse()
        receiver.onReceiveAsync(context, graph, intent(course.id, now.plusMinutes(5)))
        val notifications = postedNotifications()
        assertEquals(1, notifications.size)
        assertEquals(NotificationHelper.CHANNEL_ID, notifications[0].channelId)
        // 触发后立即补排：该课下一轮（下周六）的提醒进入计划
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `课程已删除陈旧闹钟静默跳过`() = runTest {
        val course = seedCourse()
        repo.deleteCourse(course)
        receiver.onReceiveAsync(context, graph, intent(course.id, now.plusMinutes(5)))
        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `触发日已停课静默跳过`() = runTest {
        val course = seedCourse(pausedDates = "2026-11-07")
        receiver.onReceiveAsync(context, graph, intent(course.id, now.plusMinutes(5)))
        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `提醒已关闭静默跳过`() = runTest {
        val course = seedCourse()
        repo.updateCourse(course.copy(reminderEnabled = false))
        receiver.onReceiveAsync(context, graph, intent(course.id, now.plusMinutes(5)))
        assertTrue(postedNotifications().isEmpty())
    }
}
