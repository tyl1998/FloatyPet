package com.floatypet.asset.importer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.floatypet.ai.ImageGenClient
import com.floatypet.asset.edit.ImageOps
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从视频 URI 均匀抽帧，归一化后返回 Bitmap 列表。
 *
 * 背景去除优先级（removeBackground=true 时）：
 *   1. 视觉模型已配置 → AI 检测第一帧背景色 → 精准泛洪填充。
 *   2. 无视觉模型 → 四角估算背景色 → 泛洪填充（仅适合纯色背景）。
 */
@Singleton
class VideoFrameExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageGenClient: ImageGenClient,
) {
    /**
     * @param uri              content:// URI，来自系统视频选择器（无需额外权限）
     * @param frameCount       目标帧数（1-16）
     * @param removeBackground 是否尝试去除背景（默认 false）
     */
    suspend fun extract(
        uri: Uri,
        frameCount: Int = 8,
        removeBackground: Boolean = false,
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val count = frameCount.coerceIn(1, 16)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@withContext emptyList()

            // Probe first frame once for AI background color detection.
            val aiBgColor: Int? = if (removeBackground) {
                val firstTimeUs = (0.5 * durationMs * 1000L / count).toLong()
                val firstFrame = retriever.getFrameAtTime(firstTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { ImageOps.centerSquare(ImageOps.downscale(it, 512)) }
                firstFrame?.let { imageGenClient.detectBackgroundColor(it) }
                    .also { color ->
                        Log.d("VideoExtract", "aiBgColor=${color?.let { "#%06X".format(it and 0xFFFFFF) } ?: "null→cornerEstimate"}")
                    }
            } else null

            (0 until count).mapNotNull { i ->
                val timeUs = ((i + 0.5) * durationMs * 1000L / count).toLong()
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { normalize(it, removeBackground, aiBgColor) }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            retriever.release()
        }
    }

    private fun normalize(src: Bitmap, removeBackground: Boolean, bgColor: Int?): Bitmap {
        val scaled = ImageOps.downscale(src, 512)
        val square = ImageOps.centerSquare(scaled)
        return when {
            !removeBackground -> square
            bgColor != null -> ImageOps.removeFlatBackground(square, tolerance = 36, bgColor = bgColor)
            else -> ImageOps.removeFlatBackground(square, tolerance = 36)
        }
    }
}
