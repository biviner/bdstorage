package com.zjh.timetable.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjh.timetable.data.local.entity.CourseEntity
import com.zjh.timetable.data.local.entity.PeriodTimeEntity
import com.zjh.timetable.data.local.entity.SemesterSettingEntity
import com.zjh.timetable.domain.calc.CustomWeeks
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

/**
 * DAO 层 Robolectric 测试（详细设计 §10 data 行：in-memory Room）。
 * 覆盖：CRUD、schema CHECK 反例（day_of_week=0）、customWeeks 编解码经存储往返。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimetableDatabaseTest {

    private lateinit var db: TimetableDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TimetableDatabase::class.java,
        ).allowMainThreadQueries()
            .addCallback(SchemaConstraints.callback) // 与生产装配一致，CHECK 触发器生效
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun course(
        name: String = "高等数学",
        dayOfWeek: Int = 1,
        startMinute: Int = 480,
        endMinute: Int = 580,
        weekPattern: WeekPattern = WeekPattern.ALL,
        customWeeks: String? = null,
    ) = CourseEntity(
        name = name,
        dayOfWeek = dayOfWeek,
        startMinute = startMinute,
        endMinute = endMinute,
        weekPattern = weekPattern,
        customWeeks = customWeeks,
        createdAt = LocalDateTime.of(2026, 11, 7, 12, 0),
        updatedAt = LocalDateTime.of(2026, 11, 7, 12, 0),
    )

    // ── CRUD ─────────────────────────────────────────────────────────

    @Test
    fun insert_byId_update_delete() = runTest {
        val dao = db.courseDao()

        val id = dao.insert(course())
        assertTrue(id > 0)

        val loaded = dao.byId(id)!!
        assertEquals("高等数学", loaded.name)
        assertEquals(1, loaded.dayOfWeek)
        assertEquals(480, loaded.startMinute)
        assertEquals(580, loaded.endMinute)

        dao.update(loaded.copy(name = "高等数学(下)"))
        assertEquals("高等数学(下)", dao.byId(id)!!.name)

        dao.delete(dao.byId(id)!!)
        assertNull(dao.byId(id))
    }

    @Test
    fun insertAll_returnsIds_byDaySortedByStartMinute() = runTest {
        val dao = db.courseDao()
        val ids = dao.insertAll(
            listOf(
                course(name = "B", dayOfWeek = 2, startMinute = 600),
                course(name = "A", dayOfWeek = 2, startMinute = 480),
                course(name = "C", dayOfWeek = 1),
            ),
        )
        assertEquals(3, ids.size)

        val day2 = dao.byDay(2)
        assertEquals(listOf("A", "B"), day2.map { it.name })
        assertEquals(listOf("C"), dao.byDay(1).map { it.name })
    }

    @Test
    fun setPaused_persists() = runTest {
        val dao = db.courseDao()
        val id = dao.insert(course())
        dao.setPaused(id, "2026-11-20,2026-11-27")
        assertEquals("2026-11-20,2026-11-27", dao.byId(id)!!.pausedDates)
        dao.setPaused(id, null)
        assertNull(dao.byId(id)!!.pausedDates)
    }

    @Test
    fun observeAll_ordersByDayThenStartMinute() = runTest {
        db.courseDao().insertAll(
            listOf(
                course(name = "午后", dayOfWeek = 1, startMinute = 840),
                course(name = "早八", dayOfWeek = 1, startMinute = 480),
                course(name = "周五", dayOfWeek = 5, startMinute = 480),
            ),
        )
        val names = db.courseDao().observeAll().first().map { it.name }
        assertEquals(listOf("早八", "午后", "周五"), names)
    }

    // ── schema CHECK 反例（卡面验收：day_of_week=0 被拒绝） ─────────────

    private suspend fun assertRejected(block: suspend () -> Unit) {
        val ex = runCatching { block() }.exceptionOrNull()
        assertTrue("期望 SQLiteConstraintException，实际：$ex", ex is SQLiteConstraintException)
    }

    @Test
    fun insert_dayOfWeekZero_rejectedByCheck() = runTest {
        assertRejected { db.courseDao().insert(course(dayOfWeek = 0)) }
    }

    @Test
    fun insert_startMinuteOutOfRange_rejectedByCheck() = runTest {
        assertRejected { db.courseDao().insert(course(startMinute = 1440)) }
    }

    @Test
    fun insert_endMinuteOutOfRange_rejectedByCheck() = runTest {
        assertRejected { db.courseDao().insert(course(endMinute = 1441)) }
    }

    @Test
    fun insert_semesterSettingWithIdNotOne_rejectedByCheck() = runTest {
        val bad = SemesterSettingEntity(id = 2, firstWeekStartDate = LocalDate.of(2026, 11, 9))
        assertRejected { db.semesterSettingDao().upsert(bad) }
    }

    @Test
    fun insert_invalidTimetableMode_rejectedByCheck() = runTest {
        // 未经 TypeConverter 的枚举只可能是 PERIOD/EXACT_TIME；直接用 raw SQL 验证约束存在
        assertRejected {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO semester_setting VALUES (1, '2026-11-09', 20, 15, 'WEEKLY')",
            )
        }
    }

    // ── 学期/作息 ─────────────────────────────────────────────────────

    @Test
    fun semesterSetting_upsertIsSingleRow() = runTest {
        val dao = db.semesterSettingDao()
        assertNull(dao.get())

        val first = SemesterSettingEntity(id = 1, firstWeekStartDate = LocalDate.of(2026, 11, 9))
        dao.upsert(first)
        assertEquals(first, dao.get())

        val second = first.copy(totalWeeks = 18)
        dao.upsert(second)
        assertEquals(second, dao.get())
    }

    @Test
    fun periodTime_upsertAll_clear() = runTest {
        val dao = db.periodTimeDao()
        dao.upsertAll(DefaultTimetable.seed.map { PeriodTimeEntity(it.periodIndex, it.startTime, it.endTime) })
        assertEquals(listOf(1, 3, 5, 7, 9), dao.getAll().map { it.periodIndex })

        dao.upsertAll(listOf(PeriodTimeEntity(3, LocalTime.of(10, 0), LocalTime.of(12, 0))))
        assertEquals(LocalTime.of(12, 0), dao.getAll().first { it.periodIndex == 3 }.endTime)

        dao.clear()
        assertTrue(dao.getAll().isEmpty())
    }

    // ── customWeeks 编解码经存储往返（详细设计 §3.2 用例表） ─────────────

    @Test
    fun customWeeks_roundTripThroughStorage() = runTest {
        val dao = db.courseDao()
        val cases: List<Set<Int>> = listOf(
            setOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 13, 14, 15, 16), // "1-8,10,12-16"
            setOf(1, 3, 5, 15), // 单双周以外的混排
            emptySet(), // 空串
            setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), // "16-1" 逆序归一化
            setOf(1, 21), // 超界（>totalWeeks）保留，由上层过滤
        )
        for (weeks in cases) {
            val encoded = CustomWeeks.encode(weeks)
            val id = dao.insert(course(weekPattern = WeekPattern.CUSTOM, customWeeks = encoded))
            val (decoded, warnings) = CustomWeeks.decode(dao.byId(id)!!.customWeeks)
            assertEquals(weeks, decoded)
            assertTrue("不应产生 warning：$warnings", warnings.isEmpty())
        }
    }

    @Test
    fun customWeeks_decodeTolerantOfDirtyInput() {
        val (weeks, warnings) = CustomWeeks.decode("1-3,, 5 ,16-1,abc,1-2-3")
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), weeks)
        // 非法 token 被丢弃且附 warning
        assertEquals(setOf("abc", "1-2-3"), warnings.toSet())
    }

    // ── 枚举/时间 TypeConverter 往返 ─────────────────────────────────

    @Test
    fun converters_weekPatternAndLocalTimeRoundTrip() = runTest {
        val id = db.courseDao().insert(course(weekPattern = WeekPattern.ODD))
        assertEquals(WeekPattern.ODD, db.courseDao().byId(id)!!.weekPattern)

        val row = PeriodTimeEntity(1, LocalTime.of(8, 0), LocalTime.of(9, 40))
        db.periodTimeDao().upsertAll(listOf(row))
        assertEquals(row, db.periodTimeDao().getAll().single())
    }
}
