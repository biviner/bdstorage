package com.zjh.timetable.network

import com.zjh.timetable.domain.model.PeriodTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 网关五路径单测（详细设计 §10）：正常 / 坏 JSON / 低置信度 / 4xx / 超时重试。
 * 全部走真实 OpenAiVisionRecognizer + MockWebServer，再经 RecognitionValidator。
 */
class RecognitionGatewayTest {

    private lateinit var server: MockWebServer

    /** 作息表：仅第 1-2 节，供规则 3 的节次映射用 */
    private val timetable = listOf(
        PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        PeriodTime(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
    )

    private val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gateway(callTimeoutMillis: Long = 60_000): RecognitionGateway {
        val client = OkHttpClient.Builder()
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        return RecognitionGateway(
            OpenAiVisionRecognizer(
                baseUrl = server.url("/v1").toString(),
                apiKey = "demo-key",
                model = "vision-model",
                prompt = "你是课表解析器",
                client = client,
            )
        )
    }

    /** chat.completions 响应外壳，content 为 json_object 模式下的课程 JSON 文本 */
    private fun chatBody(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"choices":[{"message":{"content":"$escaped"}}]}"""
    }

    @Test
    fun `正常路径 - 请求合规且课程清洗正确`() = runBlocking {
        val content = """
            {"courses":[
              {"name":"高等数学","dayOfWeek":"周三","periodStart":1,"periodEnd":2,
               "weeks":"1-8","weeksParsed":[1,2,3,4,5,6,7,8],
               "location":"教1-101","teacher":"张三","confidence":0.95}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(chatBody(content)))

        val outcome = gateway().recognize(fakeJpeg, timetable).getOrThrow()

        assertEquals(1, outcome.rows.size)
        assertEquals(0, outcome.droppedCount)
        val course = outcome.rows[0].course
        assertEquals("高等数学", course.name)
        assertEquals(3, course.dayOfWeek)          // 「周三」文字由代码兜底映射
        assertFalse(outcome.rows[0].needsReview)

        // 请求结构：路径 / Authorization / response_format=json_object / temperature=0 / 图片 data URI / 系统 prompt
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer demo-key", recorded.getHeader("Authorization"))
        val raw = recorded.body.readUtf8()
        val body = Json.parseToJsonElement(raw).jsonObject
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(0.0, body["temperature"]!!.jsonPrimitive.content.toDouble())
        assertTrue(raw.contains("data:image/jpeg;base64,"))
        assertTrue(raw.contains("你是课表解析器"))
        assertTrue(raw.contains("请解析这张课表截图"))
    }

    @Test
    fun `坏JSON - 返回失败文案`() = runBlocking {
        server.enqueue(MockResponse().setBody(chatBody("这不是JSON")))

        val result = gateway().recognize(fakeJpeg, timetable)

        assertTrue(result.isFailure)
        assertEquals("识别服务返回异常，请重试", result.exceptionOrNull()?.message)
    }

    @Test
    fun `低置信度 - 保留并标黄不丢弃`() = runBlocking {
        val content = """
            {"courses":[
              {"name":"大学英语","dayOfWeek":2,"periodStart":1,"periodEnd":2,
               "weeks":"单周","weeksParsed":[1,3,5],"confidence":0.4}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(chatBody(content)))

        val outcome = gateway().recognize(fakeJpeg, timetable).getOrThrow()

        assertEquals(0, outcome.droppedCount)
        val row = outcome.rows.single()
        assertTrue(row.lowConfidence)
        assertTrue(row.needsReview)
        assertEquals(2, row.course.dayOfWeek)
    }

    @Test
    fun `反例 - confidence 0_2 且 name 缺失被丢弃并计数`() = runBlocking {
        val content = """
            {"courses":[
              {"name":null,"dayOfWeek":1,"periodStart":1,"periodEnd":2,"confidence":0.2},
              {"name":"体育","dayOfWeek":4,"periodStart":1,"periodEnd":2,"confidence":0.9}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(chatBody(content)))

        val outcome = gateway().recognize(fakeJpeg, timetable).getOrThrow()

        assertEquals(1, outcome.droppedCount)          // 丢弃并计数
        assertNull(outcome.rows.firstOrNull { it.course.name == null })
        assertEquals("体育", outcome.rows.single().course.name)
    }

    @Test
    fun `4xx - 立即失败且不重试`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))

        val result = gateway().recognize(fakeJpeg, timetable)

        assertTrue(result.isFailure)
        assertEquals(
            """识别服务请求失败（HTTP 401）：{"error":"bad key"}""",
            result.exceptionOrNull()?.message,
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `4xx - 错误体密钥字样被过滤且 404 提示 baseUrl`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"error":"key sk-abc123DEF456ghi789 not found"}""")
        )

        val result = gateway().recognize(fakeJpeg, timetable)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("识别服务请求失败（HTTP 404）"))
        assertTrue(message.contains("sk-***"))
        assertFalse(message.contains("sk-abc123DEF456ghi789"))
        assertTrue(message.endsWith(" — 请检查 baseUrl 是否含 /v1"))
    }

    @Test
    fun `超时 - 网络错误重试 1 次后成功`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setBody(chatBody("""{"courses":[]}""")))

        val result = gateway(callTimeoutMillis = 500).recognize(fakeJpeg, timetable)

        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `超时 - 两次超时后失败并给出超时文案`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = gateway(callTimeoutMillis = 500).recognize(fakeJpeg, timetable)

        assertTrue(result.isFailure)
        assertEquals("识别服务连接超时，请重试", result.exceptionOrNull()?.message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `日志 - 密钥不落日志且 Authorization 头输出打码`() = runBlocking {
        val logs: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())
        val client = OpenAiVisionRecognizer.defaultClient(
            logger = HttpLoggingInterceptor.Logger { logs += it }
        )
        // 默认 BASIC 只记请求行，调到 HEADERS 实际验证 redact 输出
        client.interceptors.filterIsInstance<HttpLoggingInterceptor>().single()
            .level = HttpLoggingInterceptor.Level.HEADERS
        val recognizer = OpenAiVisionRecognizer(
            baseUrl = server.url("/v1").toString(),
            apiKey = "demo-key",
            model = "vision-model",
            prompt = "p",
            client = client,
        )
        server.enqueue(MockResponse().setBody(chatBody("""{"courses":[]}""")))

        val result = recognizer.recognize(fakeJpeg)

        assertTrue(result.isSuccess)
        val joined = logs.joinToString("\n")
        assertTrue(joined.contains("Authorization: ██"))  // 头被 redact（§9.6）
        assertFalse(joined.contains("demo-key"))          // 密钥不进日志（§9.6）
    }
}
