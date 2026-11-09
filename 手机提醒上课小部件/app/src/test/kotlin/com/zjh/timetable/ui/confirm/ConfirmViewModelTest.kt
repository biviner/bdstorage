package com.zjh.timetable.ui.confirm

import com.zjh.timetable.domain.model.RecognizedCourse
import com.zjh.timetable.network.RecognitionDraftCodec
import com.zjh.timetable.network.RecognitionOutcome
import com.zjh.timetable.network.RecognizedRow
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalTime

/** B3-2：确认页行内编辑/标黄/冲突/导入与丢弃（详细设计 §5.5、全局约束 §9.5）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfirmViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeAll
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterAll
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun course(
        name: String? = "大学英语",
        day: Int? = 3,
        // 单节次行：默认作息第 5 行 = 14:00-15:40（连续节次合并按行号索引，同 CourseEdit 约定）
        periodStart: Int? = 5,
        periodEnd: Int? = null,
        startTime: String? = null,
        endTime: String? = null,
        weeksRaw: String? = null,
        weeksParsed: List<Int> = listOf(1, 2, 3),
        confidence: Double = 0.95,
    ) = RecognizedCourse(
        name = name, dayOfWeek = day, periodStart = periodStart, periodEnd = periodEnd,
        startTime = startTime, endTime = endTime, weeksRaw = weeksRaw, weeksParsed = weeksParsed,
        location = "D404", teacher = "李老师", confidence = confidence,
    )

    /** 落一份草稿并返回 (repo, vm)。默认作息含第 5 节 14:00-15:40。 */
    private suspend fun kotlinx.coroutines.test.TestScope.prepare(
        rows: List<RecognizedRow>,
        droppedCount: Int = 0,
    ): Pair<FakeTimetableRepository, ConfirmViewModel> {
        val repo = FakeTimetableRepository()
        val draftId = repo.saveDraft(RecognitionDraftCodec.encode(RecognitionOutcome(rows, droppedCount)))
        val vm = ConfirmViewModel(repo, draftId)
        advanceUntilIdle()
        return repo to vm
    }

    @Test
    fun `confirm import persists courses and deletes draft`() = runTest(dispatcher) {
        // 名字避开 Fake 种子课程（含「大学英语」），确保断言只命中导入行
        val (repo, vm) = prepare(listOf(RecognizedRow(course(name = "识别导入课"), lowConfidence = false, periodMappingFailed = false, weeksConflict = false)))
        advanceUntilIdle()

        assertTrue(vm.state.value.canImport)
        vm.requestImport()
        assertTrue(vm.state.value.showImportConfirm) // 二次确认
        vm.confirmImport()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.importedCount)
        val courses = repo.observeCourses().first()
        assertTrue(courses.any { it.name == "识别导入课" && it.dayOfWeek == 3 })
        // 第 5 节 → 默认作息 14:00-15:40
        val saved = courses.first { it.name == "识别导入课" }
        assertEquals(14 * 60, saved.startMinute)
        assertEquals(15 * 60 + 40, saved.endMinute)
        assertNull(repo.draft(1L)) // 草稿已删
    }

    @Test
    fun `row conflicting with existing course is flagged`() = runTest(dispatcher) {
        // Fake 种子已有 高等数学：周一 8:00-9:40（480..580）；第 1 节即 8:00-9:40
        val (repo, vm) = prepare(listOf(RecognizedRow(course(name = "冲课", day = 1, periodStart = 1), lowConfidence = false, periodMappingFailed = false, weeksConflict = false)))
        advanceUntilIdle()

        assertTrue(vm.state.value.rows[0].conflictWith.contains("高等数学"))
        // 冲突不阻止导入（高亮但不阻止，§4.6）
        assertTrue(vm.state.value.canImport)
        assertTrue(repo.draftIds().isNotEmpty())
    }

    @Test
    fun `toggling weeks clears weeksConflict flag`() = runTest(dispatcher) {
        val (repo, vm) = prepare(
            listOf(RecognizedRow(course(weeksRaw = "单周", weeksParsed = listOf(2, 4, 6)), lowConfidence = false, periodMappingFailed = false, weeksConflict = true)),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.rows[0].weeksConflict)
        assertTrue(vm.state.value.rows[0].needsReview)

        vm.toggleCustomWeek(0, 2) // 用户改周次 → 视为已修正
        advanceUntilIdle()
        assertFalse(vm.state.value.rows[0].weeksConflict)
    }

    @Test
    fun `missing time info blocks import until user supplies time`() = runTest(dispatcher) {
        val (repo, vm) = prepare(
            listOf(RecognizedRow(course(name = "无时刻", periodStart = null, periodEnd = null, startTime = null, endTime = null, confidence = 0.5), lowConfidence = true, periodMappingFailed = false, weeksConflict = false)),
        )
        advanceUntilIdle()
        val row = vm.state.value.rows[0]
        assertTrue(row.missingTime)
        assertTrue(row.needsReview)
        assertFalse(vm.state.value.canImport)
        assertTrue(row.timeError != null)

        // 用户补时刻 → 可导入
        vm.updateTimes(0, LocalTime.of(18, 0), LocalTime.of(19, 40))
        advanceUntilIdle()
        assertTrue(vm.state.value.canImport)
        vm.confirmImport()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.importedCount)
    }

    @Test
    fun `discard deletes draft without writing any course`() = runTest(dispatcher) {
        val (repo, vm) = prepare(listOf(RecognizedRow(course(), lowConfidence = false, periodMappingFailed = false, weeksConflict = false)))
        advanceUntilIdle()
        val coursesBefore = repo.observeCourses().first().size

        vm.requestDiscard()
        assertTrue(vm.state.value.showDiscardConfirm)
        vm.confirmDiscard()
        advanceUntilIdle()

        assertTrue(vm.state.value.discarded)
        assertTrue(repo.draftIds().isEmpty()) // 不写库（§9.5）
        assertEquals(coursesBefore, repo.observeCourses().first().size)
    }

    @Test
    fun `missing draft navigates back via discarded`() = runTest(dispatcher) {
        val repo = FakeTimetableRepository()
        val vm = ConfirmViewModel(repo, draftId = 999L)
        advanceUntilIdle()
        assertTrue(vm.state.value.discarded)
    }

    @Test
    fun `dropped count surfaces in state`() = runTest(dispatcher) {
        val (_, vm) = prepare(rows = emptyList(), droppedCount = 3)
        advanceUntilIdle()
        assertEquals(3, vm.state.value.droppedCount)
        assertTrue(vm.state.value.rows.isEmpty())
    }
}
