package com.zjh.timetable.ui.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zjh.timetable.data.settings.RecognitionConfig
import com.zjh.timetable.data.settings.TimetableDataStore
import com.zjh.timetable.network.OpenAiVisionRecognizer
import com.zjh.timetable.network.RecognitionDraftCodec
import com.zjh.timetable.network.RecognitionGateway
import com.zjh.timetable.network.ScheduleRecognizer
import com.zjh.timetable.ui.data.UiTimetableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 识别设置门面（详细设计 §8.6 DataStore keys），导入页状态机（§5.4）的数据来源。
 * 抽出接口仅为 ViewModel 单测可替换内存实现。
 */
interface ImportSettings {
    val uploadConsentAcked: Flow<Boolean>
    val recognitionConfig: Flow<RecognitionConfig?>
    suspend fun setUploadConsentAcked(acked: Boolean)
}

/** 真实现：包装 [TimetableDataStore]。 */
class DataStoreImportSettings(private val dataStore: TimetableDataStore) : ImportSettings {
    override val uploadConsentAcked: Flow<Boolean> = dataStore.uploadConsentAcked
    override val recognitionConfig: Flow<RecognitionConfig?> = dataStore.recognitionConfig
    override suspend fun setUploadConsentAcked(acked: Boolean) {
        dataStore.setUploadConsentAcked(acked)
    }
}

/**
 * 图片导入页（详细设计 §5.4 状态机）：
 * Idle → Picking(选图) → Uploading → Recognizing → Done(draftId) | Failed(msg 可重试)。
 * 全局约束 §9.5：识别失败/未配置不写任何课程；上传声明未确认绝不发网络请求（§9.6 同源）。
 * 图片以「读取闭包」传入（ViewModel 不持有 Uri/Context，可直接单测）。
 */
class ImportViewModel(
    private val repository: UiTimetableRepository,
    private val settings: ImportSettings,
    private val prompt: String,
    private val recognizerFactory: (RecognitionConfig) -> ScheduleRecognizer =
        { config -> OpenAiVisionRecognizer(config.baseUrl, config.apiKey, config.model, prompt) },
) : ViewModel() {

    data class UiState(
        /** DataStore 首读完成前为 true，避免「未配置」闪现 */
        val loading: Boolean = true,
        /** 识别服务三项齐全（§5.4：未配置 → 入口置灰） */
        val configured: Boolean = false,
        /** 已配置的服务地址（上传声明确认弹窗展示用） */
        val baseUrl: String = "",
        /** Uploading/Recognizing 共用进度态 */
        val busy: Boolean = false,
        val busyLabel: String = "",
        /** Done(draftId) → UI 导航到确认页后调 [onNavigated] 复位 */
        val doneDraftId: Long? = null,
        /** Failed 文案，可重试 */
        val failedMessage: String? = null,
        /** 上传声明确认弹窗（§5.4：Uploading 前弹） */
        val showConsentDialog: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** 等待声明确认的图片读取闭包（不进 UiState）。 */
    private var pendingLoad: (suspend () -> ByteArray)? = null

    /** 最近一次成功读取的图片字节（Failed 重试用，§5.4） */
    private var lastImage: ByteArray? = null

    init {
        viewModelScope.launch {
            settings.recognitionConfig.collect { config ->
                _state.update {
                    it.copy(loading = false, configured = config != null, baseUrl = config?.baseUrl.orEmpty())
                }
            }
        }
    }

    /** PhotoPicker 回调（带上图片读取闭包）；未过声明 → 先弹确认（禁止：未过上传声明就发网络请求）。 */
    fun onImagePicked(load: suspend () -> ByteArray) {
        if (_state.value.busy) return
        viewModelScope.launch {
            if (settings.uploadConsentAcked.first()) {
                start(load)
            } else {
                pendingLoad = load
                _state.update { it.copy(showConsentDialog = true) }
            }
        }
    }

    /** 声明确认 → 一次性记住选择并继续。 */
    fun consentConfirmed() {
        val load = pendingLoad ?: return
        pendingLoad = null
        _state.update { it.copy(showConsentDialog = false) }
        viewModelScope.launch {
            settings.setUploadConsentAcked(true)
            start(load)
        }
    }

    fun consentDismissed() {
        pendingLoad = null
        _state.update { it.copy(showConsentDialog = false) }
    }

    /** 选图回调异常兜底（UI 层 catch 后调用）：只提示，不崩溃。 */
    fun onPickFailed() = fail("选图失败，请重试")

    /** Failed → 用最近图片重试（直接进 Recognizing：声明已确认过）。 */
    fun retry() {
        val image = lastImage ?: return
        recognize(image)
    }

    /** Done → 确认页导航完成后复位，返回本页不再立即触发导航。 */
    fun onNavigated() = _state.update { it.copy(doneDraftId = null) }

    // ── 内部 ─────────────────────────────────────────────────────────

    private fun start(load: suspend () -> ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, busyLabel = BUSY_UPLOADING, failedMessage = null) }
            try {
                val image = load()
                lastImage = image
                recognize(image)
            } catch (e: CancellationException) {
                // 结构化取消：页面退出等取消不得被吞成「图片读取失败」（评审 B3 HIGH-2）
                throw e
            } catch (e: Exception) {
                fail("图片读取失败，请重新选图")
            }
        }
    }

    private fun recognize(image: ByteArray) {
        viewModelScope.launch {
            val config = settings.recognitionConfig.first()
            if (config == null) {
                // 防御：入口已置灰，正常流程不会到这
                fail("识别服务未配置，请先在设置页配置识别服务")
                return@launch
            }
            if (config.baseUrl.toHttpUrlOrNull() == null) {
                // baseUrl 无 scheme 等非法格式会在构造 Retrofit 时抛异常崩溃（评审 B3 HIGH-1），使用前先校验
                fail("识别服务地址无效，请在设置页检查 baseUrl（需以 http:// 或 https:// 开头）")
                return@launch
            }
            _state.update { it.copy(busy = true, busyLabel = BUSY_RECOGNIZING) }
            val gateway = RecognitionGateway(recognizerFactory(config))
            gateway.recognize(image, repository.timetable())
                .onSuccess { outcome ->
                    // 识别成功才落草稿；失败不写任何数据（§9.5）
                    val draftId = repository.saveDraft(RecognitionDraftCodec.encode(outcome))
                    _state.update { it.copy(busy = false, doneDraftId = draftId) }
                }
                .onFailure { e -> fail(e.message ?: "识别失败，请重试") }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(busy = false, failedMessage = message) }

    companion object {
        const val BUSY_UPLOADING = "正在上传图片…"
        const val BUSY_RECOGNIZING = "正在识别课表…"
    }
}
