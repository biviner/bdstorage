package com.zjh.timetable.network

import com.zjh.timetable.domain.model.RecognizedCourse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容视觉模型识别实现（详细设计 §5.1，首期唯一实现）。
 * POST {baseUrl}/chat/completions；response_format=json_object、temperature=0、超时 60s、
 * 网络错误重试 1 次（HTTP 状态码错误不重试）；日志拦截器 redact Authorization（全局约束 §9.6）。
 *
 * baseUrl/apiKey/model 来自设置页（§8.5），由调用方装配后传入；prompt 文本读自本模块 assets/prompt.md。
 */
class OpenAiVisionRecognizer(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val prompt: String,
    private val client: OkHttpClient = defaultClient(),
) : ScheduleRecognizer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val service = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ChatCompletionsService::class.java)

    override suspend fun recognize(imageJpeg: ByteArray): Result<List<RecognizedCourse>> =
        withContext(Dispatchers.IO) {
            val request = buildRequest(imageJpeg)

            // 网络错误（IOException）重试 1 次；HTTP 4xx/5xx 直接失败，不重试（§5.1）
            var lastIoError: IOException? = null
            repeat(MAX_ATTEMPTS) {
                try {
                    val response = service.create("Bearer $apiKey", request)
                    if (!response.isSuccessful) {
                        // 失败文案带上服务端原因：错误体摘要（过滤密钥字样、截断），404 提示 baseUrl 可能缺 /v1（B5-FIX2）
                        val snippet = errorBodySnippet(response.errorBody()?.string(), apiKey)
                        val detail = if (snippet.isEmpty()) "" else "：$snippet"
                        val hint = if (response.code() == 404) " — 请检查 baseUrl 是否含 /v1" else ""
                        return@withContext Result.failure(
                            RecognitionFailed("识别服务请求失败（HTTP ${response.code()}）$detail$hint")
                        )
                    }
                    val bodyText = response.body()?.string().orEmpty()
                    return@withContext try {
                        Result.success(parseCourses(bodyText))
                    } catch (e: RecognitionFailed) {
                        Result.failure(e)
                    }
                } catch (e: IOException) {
                    lastIoError = e
                }
            }
            Result.failure(RecognitionFailed(networkErrorMessage(lastIoError)))
        }

    private fun buildRequest(imageJpeg: ByteArray) = ChatRequest(
        model = model,
        messages = listOf(
            ChatMessage(role = "system", content = JsonPrimitive(prompt)),
            ChatMessage(
                role = "user",
                content = buildJsonArray {
                    addJsonObject {
                        put("type", "text")
                        put("text", "请解析这张课表截图")
                    }
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", dataUri(imageJpeg))
                        }
                    }
                },
            ),
        ),
        responseFormat = ResponseFormat(type = "json_object"),
        temperature = 0.0,
    )

    /**
     * 解析 chat.completions 响应：取 choices[0].message.content（json_object 模式下本身是 JSON 文本），
     * 剥掉可能的代码围栏后解析 courses 数组，逐字段清洗为 RecognizedCourse（§5.2 规则 1、2 的清洗半）。
     * 任何一层解析失败 → RecognitionFailed「识别服务返回异常，请重试」。
     */
    private fun parseCourses(bodyText: String): List<RecognizedCourse> {
        fun bad() = RecognitionFailed("识别服务返回异常，请重试")

        val content = try {
            json.decodeFromString<ChatResponseDto>(bodyText)
                .choices.firstOrNull()?.message?.content?.trim()
        } catch (e: SerializationException) {
            throw bad()
        } catch (e: IllegalArgumentException) {
            throw bad()
        }
        if (content.isNullOrEmpty()) throw bad()

        val payload = try {
            json.decodeFromString<RawCoursesPayload>(stripFences(content))
        } catch (e: SerializationException) {
            throw bad()
        } catch (e: IllegalArgumentException) {
            throw bad()
        }
        return payload.courses.map { it.clean() }
    }

    private fun dataUri(imageJpeg: ByteArray): String =
        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageJpeg)

    companion object {
        private const val MAX_ATTEMPTS = 2

        /** 60s 超时 + 日志拦截器 redact Authorization（API key 不进日志，§9.6）。 */
        fun defaultClient(
            logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
        ): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor(logger).apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                }
            )
            .build()

        private fun networkErrorMessage(e: IOException?): String =
            if (e is SocketTimeoutException || e is InterruptedIOException) {
                "识别服务连接超时，请重试"
            } else {
                "网络异常，请检查网络后重试"
            }

        /** 容错剥除模型输出中可能包裹的 ```json 围栏。 */
        private fun stripFences(text: String): String {
            var t = text.trim()
            if (t.startsWith("```")) {
                t = t.removePrefix("```json").removePrefix("```")
                    .trim().removeSuffix("```").trim()
            }
            return t
        }
    }
}

// ---- 请求 DTO（仅编码用，响应走原始文本自行解析） ----

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat,
    val temperature: Double,
)

@Serializable
internal data class ResponseFormat(val type: String)

@Serializable
internal data class ChatMessage(val role: String, val content: JsonElement)

internal interface ChatCompletionsService {
    @POST("chat/completions")
    suspend fun create(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest,
    ): Response<ResponseBody>
}

// ---- 响应/课程 DTO：字段全部用 JsonElement 容忍模型输出的类型抖动（"周三"、字符串数字等） ----

@Serializable
internal data class ChatResponseDto(val choices: List<ChoiceDto> = emptyList())

@Serializable
internal data class ChoiceDto(val message: MessageDto? = null)

@Serializable
internal data class MessageDto(val content: String? = null)

@Serializable
internal data class RawCoursesPayload(val courses: List<RawCourse> = emptyList())

@Serializable
internal data class RawCourse(
    val name: JsonElement? = null,
    val dayOfWeek: JsonElement? = null,
    val periodStart: JsonElement? = null,
    val periodEnd: JsonElement? = null,
    val startTime: JsonElement? = null,
    val endTime: JsonElement? = null,
    @SerialName("weeks") val weeksRaw: JsonElement? = null,
    val weeksParsed: JsonElement? = null,
    val location: JsonElement? = null,
    val teacher: JsonElement? = null,
    val confidence: JsonElement? = null,
)

/** §5.2 规则 2「逐字段清洗」：类型归一、范围钳制、非法值置 null。 */
internal fun RawCourse.clean(): RecognizedCourse = RecognizedCourse(
    name = text(name),
    dayOfWeek = dayOfWeek(dayOfWeek),
    periodStart = int(periodStart),
    periodEnd = int(periodEnd),
    startTime = time(startTime),
    endTime = time(endTime),
    weeksRaw = text(weeksRaw),
    weeksParsed = weeksParsed(weeksParsed),
    location = text(location),
    teacher = text(teacher),
    confidence = confidenceValue(confidence),
)

private fun text(el: JsonElement?): String? =
    (el as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }

private fun int(el: JsonElement?): Int? =
    text(el)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

private fun confidenceValue(el: JsonElement?): Double =
    text(el)?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0

/** dayOfWeek 钳到 1..7；「周一」/「星期一」/Mon 等文字由代码兜底映射（§5.2 规则 2）。 */
private fun dayOfWeek(el: JsonElement?): Int? {
    val t = text(el) ?: return null
    (t.toIntOrNull() ?: t.toDoubleOrNull()?.toInt())?.let { return it.coerceIn(1, 7) }
    return DAY_ALIASES[t.lowercase()]
}

private fun time(el: JsonElement?): String? {
    val t = text(el) ?: return null
    val m = HH_MM.matchEntire(t) ?: return null
    val (h, min) = m.destructured
    val hour = h.toInt()
    val minute = min.toInt()
    if (hour > 23 || minute > 59) return null
    return "%02d:%02d".format(hour, minute)
}

private fun weeksParsed(el: JsonElement?): List<Int> =
    (el as? JsonArray)?.mapNotNull { int(it) }?.filter { it >= 1 }?.distinct()?.sorted()
        ?: emptyList()

private val HH_MM = Regex("""^(\d{1,2}):(\d{2})$""")

private val DAY_ALIASES: Map<String, Int> = buildMap {
    val zhChars = listOf("一", "二", "三", "四", "五", "六", "日")
    val enShort = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val enLong = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    for (i in 0..6) {
        val n = i + 1
        put("周${zhChars[i]}", n)
        put("星期${zhChars[i]}", n)
        put("周$n", n)
        put(enShort[i], n)
        put(enLong[i], n)
    }
    put("星期天", 7)
}
