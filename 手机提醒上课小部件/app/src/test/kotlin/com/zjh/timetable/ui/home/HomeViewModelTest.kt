package com.zjh.timetable.ui.home

import com.zjh.timetable.domain.model.CourseStatus
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.text.WidgetText
import com.zjh.timetable.ui.data.FakeTimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime

/** B2-3：首页下一节卡片与今日时间线状态推导（详细设计 §8.2、§7.2）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeAll
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterAll
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 学期第 1 周周一（任意确定日期），Fake 种子课程对齐：周一 2 节课等。 */
    private val monday = LocalDate.of(2026, 11, 2)

    private suspend fun vmAt(time: LocalDateTime): Pair<HomeViewModel, FakeTimetableRepository> {
        val repo = FakeTimetableRepository()
        // 覆盖 Fake 默认学期，保证「第 1 周」确定性（种子里 体育=ODD、数据结构=CUSTOM 1-8 均在本周生效）
        repo.saveSemester(SemesterSetting(firstWeekStartDate = monday))
        val vm = HomeViewModel(repo, nowFlow = flowOf(time))
        return vm to repo
    }

    private suspend fun kotlinx.coroutines.test.TestScope.stateOf(vm: HomeViewModel): HomeViewModel.UiState {
        val job = launch { vm.state.collect { } }
        advanceUntilIdle()
        val snapshot = vm.state.value
        job.cancel()
        return snapshot
    }

    @Test
    fun `next class card before first lesson`() = runTest(dispatcher) {
        val (vm, _) = vmAt(monday.atTime(7, 50))
        val state = stateOf(vm)

        val next = state.nextClass as WidgetText.Model.NextClass
        assertEquals("高等数学", next.name) // 今日最早未开始
        assertEquals("08:00", next.startText)
        assertEquals("距上课还有 10 分钟", next.countdown)

        val rows = state.todayCourses
        assertEquals(listOf("高等数学", "算法设计"), rows.map { it.course.name }) // 仅周一两节
        assertTrue(rows[0].isNext)
        assertFalse(rows[0].status == CourseStatus.Ongoing)
    }

    @Test
    fun `ongoing and next statuses mid morning`() = runTest(dispatcher) {
        val (vm, _) = vmAt(monday.atTime(9, 0))
        val state = stateOf(vm)

        val next = state.nextClass as WidgetText.Model.NextClass
        assertEquals("算法设计", next.name) // 高等数学(8:00-9:40) 进行中，最早未开始为算法设计

        val rows = state.todayCourses
        assertEquals(CourseStatus.Ongoing, rows[0].status)
        assertEquals(CourseStatus.NotStarted, rows[1].status)
        assertFalse(rows[0].isNext)
        assertTrue(rows[1].isNext)
    }

    @Test
    fun `all ended after last lesson`() = runTest(dispatcher) {
        val (vm, _) = vmAt(monday.atTime(11, 0))
        val state = stateOf(vm)

        assertTrue(state.nextClass is WidgetText.Model.AllEnded)
        assertTrue(state.todayCourses.all { it.status == CourseStatus.Ended })
        assertTrue(state.todayCourses.none { it.isNext })
    }

    @Test
    fun `other weekday courses excluded from today`() = runTest(dispatcher) {
        val (vm, _) = vmAt(monday.atTime(7, 50))
        val state = stateOf(vm)

        // 数据结构(周二)、大学英语(周三)、体育(周五) 不进今日时间线
        assertFalse(state.todayCourses.any { it.course.name in setOf("数据结构", "大学英语", "体育") })
        assertEquals(1, state.currentWeek)
    }
}
