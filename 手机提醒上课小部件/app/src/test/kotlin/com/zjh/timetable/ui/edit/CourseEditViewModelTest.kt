package com.zjh.timetable.ui.edit

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.ui.data.FakeTimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.time.LocalTime

/** B2-2：课程编辑保存校验与冲突弹窗流程（详细设计 §8.4、§4.6）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeAll
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterAll
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun existingCourse(name: String, day: Int, start: Int, end: Int) = Course(
        id = 0, name = name, dayOfWeek = day, startMinute = start, endMinute = end,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
    )

    private suspend fun FakeTimetableRepository.courseNames() =
        observeCourses().first().map { it.name }

    @Test
    fun `period beyond timetable sets fieldError`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        repo.replaceTimetable(emptyList()) // 作息为空 → 任何节次映射失败
        val vm = CourseEditViewModel(repo, CourseEditViewModel.NEW_COURSE_ID)
        advanceUntilIdle()
        vm.updateName("测试课")
        vm.save()
        advanceUntilIdle()
        assertEquals("节次超出作息表，请到设置页补全作息时间", vm.state.value.fieldError)
        assertTrue(vm.state.value.pendingConflicts.isEmpty())
        assertTrue(repo.courseNames().none { it == "测试课" })
    }

    @Test
    fun `exact time end before start sets fieldError`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        val vm = CourseEditViewModel(repo, CourseEditViewModel.NEW_COURSE_ID)
        advanceUntilIdle()
        vm.updateName("测试课")
        vm.updateUsePeriod(false)
        vm.updateTimes(LocalTime.of(10, 0), LocalTime.of(9, 0))
        vm.save()
        advanceUntilIdle()
        assertEquals("结束时间需晚于开始时间", vm.state.value.fieldError)
        assertTrue(repo.courseNames().none { it == "测试课" })
    }

    @Test
    fun `save without conflict persists immediately`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        val vm = CourseEditViewModel(repo, CourseEditViewModel.NEW_COURSE_ID)
        advanceUntilIdle()
        vm.updateName("大学物理")
        vm.updateDay(4) // 周四无种子课程，默认时段（周一第 1 节）无冲突
        vm.save()
        advanceUntilIdle()
        assertNull(vm.state.value.fieldError)
        assertTrue(vm.state.value.done)
        assertTrue(repo.courseNames().contains("大学物理"))
    }

    @Test
    fun `conflicting save raises dialog and confirmSaveAnyway persists`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        // 种子里已有 高等数学：周一 8:00-9:40（480..580）
        val vm = CourseEditViewModel(repo, CourseEditViewModel.NEW_COURSE_ID)
        advanceUntilIdle()
        vm.updateName("冲突课")
        vm.updateDay(1)
        vm.save()
        advanceUntilIdle()
        // 默认时段 = 周一第 1 节（480..580），与 高等数学(480..580)、算法设计(560..640) 均重叠
        assertEquals(listOf("高等数学", "算法设计"), vm.state.value.pendingConflicts)

        vm.confirmSaveAnyway()
        advanceUntilIdle()
        assertTrue(vm.state.value.pendingConflicts.isEmpty())
        assertTrue(vm.state.value.done)
        assertTrue(repo.courseNames().contains("冲突课"))
    }

    @Test
    fun `editing existing course excludes itself from conflicts`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        val id = repo.addCourse(existingCourse("自身", 2, 480, 580))
        val vm = CourseEditViewModel(repo, id)
        advanceUntilIdle()
        vm.save() // 不改任何字段直接保存
        advanceUntilIdle()
        assertTrue(vm.state.value.pendingConflicts.isEmpty())
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `delete removes course`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        val id = repo.addCourse(existingCourse("待删除", 3, 480, 580))
        val vm = CourseEditViewModel(repo, id)
        advanceUntilIdle()
        vm.requestDelete()
        assertTrue(vm.state.value.showDeleteConfirm)
        vm.confirmDelete()
        advanceUntilIdle()
        assertTrue(vm.state.value.done)
        assertNull(repo.course(id))
    }
}
