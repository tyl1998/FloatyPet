package com.floatypet.asset.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.floatypet.asset.edit.ImageOps
import com.floatypet.asset.store.PetStore
import com.floatypet.core.model.PetAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从相册导入图片序列作为指定动作的帧。
 *
 * 处理管线：解码 → 缩放至 512 → 裁方 → 可选去背景（平色底）→ 写帧文件。
 * 与 [VideoFrameExtractor] 并列：视频适合拍连续动作，图片适合多姿势精拍。
 */
@Singleton
class FrameImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val petStore: PetStore,
) {
    data class ImportResult(val savedCount: Int, val firstFrame: Bitmap?)

    /**
     * 将多张图片 URI 按顺序导入为指定动作的帧序列。
     *
     * @param uris            用户选择的图片 URI 列表（content://）
     * @param removeBackground 是否尝试自动去除纯色背景；对白底照片效果最佳
     */
    suspend fun importImages(
        petId: String,
        action: PetAction,
        uris: List<Uri>,
        removeBackground: Boolean = true,
    ): ImportResult = withContext(Dispatchers.IO) {
        var saved = 0
        var first: Bitmap? = null
        uris.forEachIndexed { idx, uri ->
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@runCatching
                val processed = process(raw, removeBackground)
                petStore.writeFrame(petId, action, frameIndex = idx + 1, bitmap = processed)
                if (first == null) first = processed
                saved++
            }
        }
        ImportResult(saved, first)
    }

    private fun process(src: Bitmap, removeBackground: Boolean): Bitmap {
        val scaled = ImageOps.downscale(src, 512)
        val square = ImageOps.centerSquare(scaled)
        return if (removeBackground) ImageOps.removeFlatBackground(square, tolerance = 40) else square
    }
}
