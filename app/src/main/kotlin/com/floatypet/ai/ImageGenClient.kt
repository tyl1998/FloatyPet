package com.floatypet.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容图像生成客户端。
 *
 * 仅用于调用用户配置的 AI 服务端点，不做任何其他网络请求（CLAUDE.md §8）。
 * API Key 通过 Authorization 头传输，不写入任何日志。
 */
@Singleton
class ImageGenClient @Inject constructor(
    private val configStore: AiConfigStore,
) {
    // 图像生成耗时长，超时设置较宽
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * 发送文生图请求，返回解码好的 Bitmap。
     * 失败时抛出 [IOException]（调用方按 PRD §1.2 做降级回退）。
     */
    suspend fun generateImageBitmap(prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val cfg = configStore.config.first()
        check(cfg.isConfigured) { "AI 服务未配置" }

        // Step 1: 请求生成，拿到图片 URL
        val imageUrl = requestImageUrl(cfg, prompt)

        // Step 2: 下载图片字节流并解码
        val bitmap = downloadBitmap(imageUrl)
            ?: throw IOException("图片下载后解码失败")
        bitmap
    }

    /** 调用 /images/generations 端点，返回图片 URL。 */
    private fun requestImageUrl(cfg: AiConfigStore.AiConfig, prompt: String): String {
        val body = JSONObject().apply {
            put("model", cfg.genModel)
            put("prompt", prompt)
            put("size", "1024x1024")
            put("n", 1)
            put("response_format", "url")
        }.toString().toRequestBody(jsonType)

        val request = Request.Builder()
            .url("${cfg.baseUrl}/images/generations")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(body)
            .build()

        val raw = client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful || respBody == null) {
                throw IOException("生成请求失败 HTTP ${resp.code}: ${respBody?.take(200)}")
            }
            respBody
        }

        return try {
            JSONObject(raw).getJSONArray("data").getJSONObject(0).getString("url")
        } catch (e: Exception) {
            throw IOException("解析生成响应失败：${raw.take(200)}")
        }
    }

    /** GET 图片 URL，解码为 Bitmap。 */
    private fun downloadBitmap(url: String): Bitmap? {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: return@use null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    /**
     * 测试连通性：GET {baseUrl}/models。
     * 收到任意 HTTP 响应（包括 401/403）表示端点可达，网络异常则返回错误描述。
     */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code in 200..299 || resp.code in 400..403) {
                        "连通正常（HTTP ${resp.code}）"
                    } else {
                        throw IOException("HTTP ${resp.code}")
                    }
                }
            }
        }
}
