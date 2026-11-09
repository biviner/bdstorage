package com.zjh.timetable.ui.import

import com.zjh.timetable.data.settings.RecognitionConfig
import com.zjh.timetable.domain.model.RecognizedCourse
import com.zjh.timetable.network.RecognitionFailed
import com.zjh.timetable.network.ScheduleRecognizer
import com.zjh.timetable.ui.data.FakeTimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** B3-2：导入页状态机与上传声明门禁（详细设计 §5.4、全局约束 §9.5/§9.6）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeAll
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterAll
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeImportSettings : ImportSettings {
        val acked = MutableStateFlow(false)
        val config = MutableStateFlow<RecognitionConfig?>(
            RecognitionConfig("https://mock.local", "key", "model"),
        )
        override val uploadConsentAcked = acked
        override val recognitionConfig = config
        override suspend fun setUploadConsentAcked(acked: Boolean) {
            this.acked.value = acked
        }
    }

    /** 可编程假识别器：记录调用次数与图片字节，逐次返回预设结果。 */
    private class FakeRecognizer : ScheduleRecognizer {
        val calls = mutableListOf<ByteArray>()
        private val results = ArrayDeque<Result<List<RecognizedCourse>>>()

        fun enqueue(result: Result<List<RecognizedCourse>>) {
            results += result
        }

        override suspend fun recognize(imageJpeg: ByteArray): Result<List<RecognizedCourse>> {
            calls += imageJpeg
            return results.removeFirstOrNull()
                ?: Result.failure(RecognitionFailed("识别服务返回异常，请重试"))
        }
    }

    private fun validCourse(name: String = "大学英语") = RecognizedCourse(
        name = name, dayOfWeek = 3, periodStart = 5, periodEnd = null,
        startTime = null, endTime = null, weeksRaw = "1-3", weeksParsed = listOf(1, 2, 3),
        location = "D404", teacher = "李老师", confidence = 0.95,
    )

    private fun viewModel(
        settings: FakeImportSettings = FakeImportSettings(),
        recognizer: FakeRecognizer = FakeRecognizer(),
    ): Pair<ImportViewModel, FakeTimetableRepository> {
        val repo = FakeTimetableRepository()
        val vm = ImportViewModel(
            repository = repo,
            settings = settings,
            prompt = "prompt",
            recognizerFactory = { recognizer },
        )
        return vm to repo
    }

    /** 替代真实图片解码的假读取闭包（内容即调用电位标记）。 */
    private val fakeLoad: suspend () -> ByteArray = { "fake-image".toByteArray() }

    @Test
    fun `picking without consent shows dialog and sends no network request`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        advanceUntilIdle()
        vm.onImagePicked(fakeLoad)
        advanceUntilIdle()
        assertTrue(vm.state.value.showConsentDialog)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.doneDraftId)
    }

    @Test
    fun `consent confirm persists ack and saves draft`() = runTest(dispatcher) {
        val settings = FakeImportSettings()
        val recognizer = FakeRecognizer()
        recognizer.enqueue(Result.success(listOf(validCourse())))
        val (vm, repo) = viewModel(settings, recognizer)
        advanceUntilIdle()

        vm.onImagePicked(fakeLoad)
        advanceUntilIdle()
        vm.consentConfirmed()
        advanceUntilIdle()

        assertTrue(settings.acked.value) // 一次性记住选择（§5.4）
        assertEquals(1, recognizer.calls.size)
        val draftId = vm.state.value.doneDraftId
        assertNotNull(draftId)
        assertNotNull(repo.draft(draftId!!))
    }

    @Test
    fun `recognition failure shows message and writes nothing`() = runTest(dispatcher) {
        val recognizer = FakeRecognizer()
        recognizer.enqueue(Result.failure(RecognitionFailed("识别服务返回异常，请重试")))
        val (vm, repo) = viewModel(recognizer = recognizer)
        advanceUntilIdle()

        vm.onImagePicked(fakeLoad)
        advanceUntilIdle()
        vm.consentConfirmed()
        advanceUntilIdle()

        assertEquals("识别服务返回异常，请重试", vm.state.value.failedMessage)
        assertNull(vm.state.value.doneDraftId)
        // §9.5：失败不写半成品
        assertTrue(repo.draftIds().isEmpty())
    }

    @Test
    fun `retry reuses last image and succeeds`() = runTest(dispatcher) {
        val recognizer = FakeRecognizer()
        recognizer.enqueue(Result.failure(RecognitionFailed("网络异常，请检查网络后重试")))
        recognizer.enqueue(Result.success(listOf(validCourse())))
        val (vm, _) = viewModel(recognizer = recognizer)
        advanceUntilIdle()

        vm.onImagePicked(fakeLoad)
        advanceUntilIdle()
        vm.consentConfirmed()
        advanceUntilIdle()
        assertNull(vm.state.value.doneDraftId)

        vm.retry()
        advanceUntilIdle()
        assertEquals(2, recognizer.calls.size)
        assertNotNull(vm.state.value.doneDraftId)
    }

    @Test
    fun `unconfigured service refuses recognition without network`() = runTest(dispatcher) {
        val settings = FakeImportSettings()
        settings.config.value = null
        val recognizer = FakeRecognizer()
        val (vm, _) = viewModel(settings, recognizer)
        advanceUntilIdle()

        assertFalse(vm.state.value.configured) // 入口置灰依据
        vm.onImagePicked(fakeLoad)
        advanceUntilIdle()
        vm.consentConfirmed()
        advanceUntilIdle()

        assertNotNull(vm.state.value.failedMessage)
        assertEquals(0, recognizer.calls.size)
    }

    @Test
    fun `schemeless baseUrl is rejected with hint and never calls recognizer`() = runTest(dispatcher) {
        // 评审 B3 HIGH-1：无 scheme 地址原会令 Retrofit 构造抛 IllegalArgumentException 确定性崩溃
        val settings = FakeImportSettings()
        settings.config.value = RecognitionConfig("api.openai.com/v1", "key", "model")
        val recognizer = FakeRecognizer()
        val (vm, repo) = viewModel(settings, recognizer)
        advanceUntilIdle()

        vm.onImagePicked(fakeLoad)
        advanceUntilIdle()
        vm.consentConfirmed()
        advanceUntilIdle()

        assertEquals("识别服务地址无效，请在设置页检查 baseUrl（需以 http:// 或 https:// 开头）", vm.state.value.failedMessage)
        assertFalse(vm.state.value.busy)
        assertEquals(0, recognizer.calls.size)
        assertNull(vm.state.value.doneDraftId)
        assertTrue(repo.draftIds().isEmpty()) // §9.5：不写任何课程
    }

    @Test
    fun `cancellation during image load is rethrown not reported as failure`() = runTest(dispatcher) {
        // 评审 B3 HIGH-2：CancellationException 必须先 rethrow，不得吞成「图片读取失败」
        val settings = FakeImportSettings()
        settings.acked.value = true
        val (vm, _) = viewModel(settings)
        advanceUntilIdle()

        vm.onImagePicked { throw CancellationException("page left") }
        advanceUntilIdle()

        assertNull(vm.state.value.failedMessage) // 未被吞成失败文案
        assertTrue(vm.state.value.busy)          // 协程随取消而中止，停在 busy（页面已退出）
    }
}
