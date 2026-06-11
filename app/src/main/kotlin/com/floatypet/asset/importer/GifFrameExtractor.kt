package com.floatypet.asset.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.floatypet.ai.ImageGenClient
import com.floatypet.asset.edit.ImageOps
import com.floatypet.asset.store.PetStore
import com.floatypet.core.model.PetAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 GIF 文件中均匀抽帧，处理后存储为指定动作的帧序列。
 *
 * 处理管线：GIF 解码 → 均匀采样 → 缩放至 512 → 裁方 → 去背景 → 写帧文件。
 * 背景去除优先级（逐级降级）：
 *   1. GIF 原生带透明度 → 直接保留，跳过所有去除逻辑。
 *   2. 视觉模型已配置 → AI 检测第一帧背景色 → 精准泛洪填充。
 *   3. 无视觉模型 → 四角估算背景色 → 泛洪填充（仅适合纯色背景）。
 * 使用系统内置的 [Movie] 类，无需第三方依赖（CLAUDE.md §9）。
 */
@Singleton
class GifFrameExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val petStore: PetStore,
    private val imageGenClient: ImageGenClient,
) {

    data class ExtractResult(val savedCount: Int, val firstFrame: Bitmap?)

    /**
     * @param uri       GIF 文件的 content:// URI
     * @param maxFrames 最多保留帧数，默认 12
     */
    @Suppress("DEPRECATION") // Movie is deprecated in API 33 but still functional; no alternative without a library
    suspend fun extract(
        petId: String,
        action: PetAction,
        uri: Uri,
        maxFrames: Int = 12,
        removeBackground: Boolean = true,
    ): ExtractResult = withContext(Dispatchers.IO) {
        val movie = context.contentResolver.openInputStream(uri)?.use {
            Movie.decodeStream(it)
        } ?: return@withContext ExtractResult(0, null)

        val w = movie.width()
        val h = movie.height()
        val duration = movie.duration()
        if (w <= 0 || h <= 0 || duration <= 0) return@withContext ExtractResult(0, null)

        val step = (duration.toFloat() / maxFrames).toInt().coerceAtLeast(1)
        val rawBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rawBmp)
        val paint = Paint()

        var saved = 0
        var first: Bitmap? = null
        var time = 0

        // Step 1: native transparency check — preserve as-is if present.
        rawBmp.eraseColor(Color.TRANSPARENT)
        movie.setTime(0)
        movie.draw(canvas, 0f, 0f, paint)
        val gifHasTransparency = hasTransparentPixels(rawBmp)
        Log.d("GifExtract", "gifHasTransparency=$gifHasTransparency removeBackground=$removeBackground")

        // Step 2: AI background color detection (one call, covers all frames).
        val aiBgColor: Int? = if (removeBackground && !gifHasTransparency) {
            imageGenClient.detectBackgroundColor(rawBmp)
        } else null
        Log.d("GifExtract", "aiBgColor=${aiBgColor?.let { "#%06X".format(it and 0xFFFFFF) } ?: "null→cornerEstimate"}")

        time = 0
        while (time < duration && saved < maxFrames) {
            rawBmp.eraseColor(Color.TRANSPARENT)
            movie.setTime(time)
            movie.draw(canvas, 0f, 0f, paint)

            val processed = process(rawBmp, removeBackground && !gifHasTransparency, aiBgColor)
            petStore.writeFrame(petId, action, frameIndex = saved + 1, bitmap = processed)
            if (first == null) first = processed
            saved++
            time += step
        }

        rawBmp.recycle()
        ExtractResult(saved, first)
    }

    private fun process(src: Bitmap, removeBackground: Boolean, bgColor: Int? = null): Bitmap {
        val scaled = ImageOps.downscale(src, 512)
        val square = ImageOps.centerSquare(scaled)
        return when {
            !removeBackground -> square
            bgColor != null -> ImageOps.removeFlatBackground(square, tolerance = 36, bgColor = bgColor)
            else -> ImageOps.removeFlatBackground(square, tolerance = 36)
        }
    }

    private fun hasTransparentPixels(bmp: Bitmap): Boolean {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return pixels.any { Color.alpha(it) < 255 }
    }
}
