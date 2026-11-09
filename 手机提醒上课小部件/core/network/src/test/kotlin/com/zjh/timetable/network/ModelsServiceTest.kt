package com.zjh.timetable.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 模型列表拉取单测（B5-FIX2）：正常列表解析去重排序 / 404、401 错误体进文案 / 错误体密钥字样过滤。
 */
class ModelsServiceTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun service() = ModelsService(server.url("/v1").toString(), "demo-key")

    @Test
    fun `正常 - 解析模型列表并去重排序`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"object":"list","data":[
                    {"id":"gpt-4o","object":"model"},
                    {"id":"claude-3"},
                    {"id":"gpt-4o"},
                    {"id":null}
                ]}
                """.trimIndent()
            )
        )

        val result = service().fetchModels()

        assertTrue(result.isSuccess)
        assertEquals(listOf("claude-3", "gpt-4o"), result.getOrThrow())
        val recorded = server.takeRequest()
        assertEquals("/v1/models", recorded.path)
        assertEquals("Bearer demo-key", recorded.getHeader("Authorization"))
    }

    @Test
    fun `404 - 错误体进文案带 HTTP 码`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val result = service().fetchModels()

        assertTrue(result.isFailure)
        assertEquals("获取模型列表失败（HTTP 404）：Not Found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `401 - 错误体进文案`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid API key"}"""))

        val result = service().fetchModels()

        assertTrue(result.isFailure)
        assertEquals(
            """获取模型列表失败（HTTP 401）：{"error":"Invalid API key"}""",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `错误体含 sk- 密钥字样被过滤`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":"Incorrect API key provided: sk-abc123DEF456ghi789"}""")
        )

        val result = service().fetchModels()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("sk-***"))
        assertFalse(message.contains("sk-abc123DEF456ghi789"))
    }

    @Test
    fun `错误体含当前 apiKey 被过滤`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":"bad key demo-key provided"}""")
        )

        val result = service().fetchModels()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertFalse(message.contains("demo-key"))
        assertTrue(message.contains("***"))
    }

    @Test
    fun `超长错误体截断到 200 字符`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("x".repeat(500)))

        val result = service().fetchModels()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        // 前缀 + 200 字符摘要
        assertEquals("获取模型列表失败（HTTP 500）：".length + 200, message.length)
    }

    @Test
    fun `响应非 JSON - 返回格式异常文案`() = runBlocking {
        server.enqueue(MockResponse().setBody("这不是JSON"))

        val result = service().fetchModels()

        assertTrue(result.isFailure)
        assertEquals("获取模型列表失败：服务返回格式异常", result.exceptionOrNull()?.message)
    }
}
