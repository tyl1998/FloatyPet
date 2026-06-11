package com.floatypet.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
     * 用视觉模型分析宠物照片，返回用于生成的角色外观描述。
     * 需要在 AiConfig 中配置 visionModel。
     *
     * @param bitmap 宠物照片（会压缩为最大 512px 后 base64 编码）
     * @return 角色描述字符串，如「橘白色短毛猫，圆脸，橙色眼睛，白色下巴和爪子」
     * @throws IOException 视觉模型未配置或请求失败
     */
    suspend fun analyzeImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val cfg = configStore.config.first()
        check(cfg.hasVisionModel) { "未配置视觉分析模型" }

        // 压缩图片为 JPEG base64（减小请求体积）
        val scaled = if (bitmap.width > 512 || bitmap.height > 512) {
            val ratio = 512f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("model", cfg.visionModel)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", "请详细描述这只宠物的外观特征，用于 AI 绘画角色设定。" +
                                "包括：动物种类、毛发颜色、花纹特征、脸部特征、体型特点。" +
                                "直接输出描述词，用逗号分隔，不要分析句式。示例：橘白色短毛猫，圆脸，黄色眼睛，白色下巴和爪子，背部橙色虎纹")
                    })
                })
            }))
            put("max_tokens", 200)
        }.toString().toRequestBody(jsonType)

        val req = Request.Builder()
            .url("${cfg.baseUrl}/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(body)
            .build()

        val raw = client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful || respBody == null) {
                throw IOException("视觉分析请求失败 HTTP ${resp.code}: ${respBody?.take(200)}")
            }
            respBody
        }

        try {
            JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            throw IOException("解析视觉分析响应失败：${raw.take(200)}")
        }
    }

    /**
     * 用视觉模型检测图像的主背景色，返回 Android Color int。
     * 主要用于 GIF/视频导入时的精准去背景。发送 128px 缩略图，开销极小。
     *
     * @return 检测到的背景色；视觉模型未配置或请求失败时返回 null
     */
    suspend fun detectBackgroundColor(bitmap: Bitmap): Int? = withContext(Dispatchers.IO) {
        val cfg = configStore.config.first()
        if (!cfg.hasVisionModel) return@withContext null
        runCatching {
            val size = 128
            val scaled = if (bitmap.width > size || bitmap.height > size) {
                val ratio = size.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else bitmap
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            Log.d("BgDetect", "detectBackgroundColor: calling vision model ${cfg.visionModel}")

            val body = JSONObject().apply {
                put("model", cfg.visionModel)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "What is the background color of this image? " +
                                "Reply with ONLY a 6-character uppercase hex color code (e.g. FFFFFF for white). " +
                                "No explanation, just the hex code.")
                        })
                    })
                }))
                put("max_tokens", 20)
            }.toString().toRequestBody(jsonType)

            val req = Request.Builder()
                .url("${cfg.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .post(body)
                .build()

            val raw = client.newCall(req).execute().use { resp ->
                resp.body?.string() ?: throw IOException("空响应")
            }
            Log.d("BgDetect", "detectBackgroundColor: raw response = ${raw.take(300)}")
            val text = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            Log.d("BgDetect", "detectBackgroundColor: model reply = $text")

            val hex = Regex("[0-9A-Fa-f]{6}").find(text)?.value
                ?: throw IOException("响应中未找到颜色代码: $text")
            Log.d("BgDetect", "detectBackgroundColor: detected bgColor = #$hex")
            Color.parseColor("#$hex")
        }.onFailure { Log.e("BgDetect", "detectBackgroundColor failed", it) }
         .getOrNull()
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
