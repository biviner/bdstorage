package com.zjh.timetable.scheduler

import android.app.AlarmManager
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * BootReceiver 单测（详细设计 §6.1：BOOT_COMPLETED → rescheduleAll 重建全部闹钟）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var db: TimetableDatabase
    private lateinit var repo: TimetableRepository
    private lateinit var graph: ReminderGraph
    private val receiver = BootReceiver()

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `开机广播重建全部闹钟`() = runTest {
        repo.saveSemester(
            SemesterSetting(firstWeekStartDate = now.toLocalDate().minusDays(5), totalWeeks = 20),
        )
        repo.addCourse(
            Course(
                id = 0,
                name = "高等数学",
                dayOfWeek = 6,
                startMinute = 14 * 60,
                endMinute = 15 * 60 + 40,
                weekPattern = WeekPattern.ALL,
                reminderEnabled = true,
                reminderLeadMinutes = 10,
                createdAt = now,
                updatedAt = now,
            ),
        )
        receiver.onReceiveAsync(graph)
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }

    @Test
    fun `非 BOOT_COMPLETED 广播忽略`() {
        // action 守卫：非开机广播不进入重排
        receiver.onReceive(context, Intent("android.intent.action.AIRPLANE_MODE"))
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
}
