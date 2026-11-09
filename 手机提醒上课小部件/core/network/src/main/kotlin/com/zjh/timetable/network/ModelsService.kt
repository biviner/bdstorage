package com.zjh.timetable.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * 模型列表拉取（B5-FIX2）：GET {baseUrl}/models（OpenAI 兼容标准），
 * Authorization Bearer 同识别请求；走 defaultClient（60s 超时 / 日志 redact Authorization）。
 * 每次现拉不持久化，避免列表过期。
 */
class ModelsService(
    baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OpenAiVisionRecognizer.defaultClient(),
) {
    private val url = baseUrl.trimEnd('/') + "/models"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 成功返回去重排序后的模型 id 列表；失败 message 为可直接展示的文案。 */
    suspend fun fetchModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val snippet = errorBodySnippet(response.body?.string(), apiKey)
                    val detail = if (snippet.isEmpty()) "" else "：$snippet"
                    return@withContext Result.failure(
                        ModelsFetchFailed("获取模型列表失败（HTTP ${response.code}）$detail")
                    )
                }
                val bodyText = response.body?.string().orEmpty()
                val ids = try {
                    json.decodeFromString<ModelsResponseDto>(bodyText)
                        .data.mapNotNull { it.id?.trim() }
                        .filter { it.isNotEmpty() }
                } catch (e: SerializationException) {
                    return@withContext Result.failure(
                        ModelsFetchFailed("获取模型列表失败：服务返回格式异常")
                    )
                } catch (e: IllegalArgumentException) {
                    return@withContext Result.failure(
                        ModelsFetchFailed("获取模型列表失败：服务返回格式异常")
                    )
                }
                Result.success(ids.distinct().sorted())
            }
        } catch (e: IOException) {
            Result.failure(ModelsFetchFailed(modelsNetworkErrorMessage(e)))
        }
    }

    private fun modelsNetworkErrorMessage(e: IOException): String =
        if (e is SocketTimeoutException || e is InterruptedIOException) {
            "获取模型列表连接超时，请重试"
        } else {
            "网络异常，请检查网络后重试"
        }
}

/** 模型列表拉取失败异常，message 即设置页可直接展示的文案（B5-FIX2）。 */
class ModelsFetchFailed(message: String) : Exception(message)

/**
 * 服务端错误体摘要（B5-FIX2）：过滤 sk- 密钥字样与当前 apiKey、压成单行、截断 200 字符。
 */
internal fun errorBodySnippet(body: String?, apiKey: String): String {
    if (body.isNullOrBlank()) return ""
    var t = body
        .replace(Regex("sk-[A-Za-z0-9_-]{4,}"), "sk-***")
        .let { if (apiKey.isNotBlank()) it.replace(apiKey, "***") else it }
        .replace(Regex("\\s+"), " ")
        .trim()
    if (t.length > 200) t = t.take(200)
    return t
}

@Serializable
internal data class ModelsResponseDto(val data: List<ModelEntryDto> = emptyList())

@Serializable
internal data class ModelEntryDto(val id: String? = null)
