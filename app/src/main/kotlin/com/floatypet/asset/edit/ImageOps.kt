package com.floatypet.asset.edit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import androidx.core.graphics.set
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 纯函数图像处理：裁剪 + 纯色背景抠图 + 橡皮擦。零第三方依赖（CLAUDE.md §9）。
 */
object ImageOps {

    /** 等比缩放，使最长边不超过 maxEdge，避免大图占内存（性能红线 §7）。 */
    fun downscale(src: Bitmap, maxEdge: Int = 1024): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true,
        )
    }

    /** 居中裁成正方形（边长取较短边）。 */
    fun centerSquare(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }

    /**
     * 纯色背景抠图：从四角泛洪填充，移除与角点颜色相近（容差内）的连通区域。
     * 适合白底/纯色底照片。结果带透明通道。
     *
     * @param tolerance 0..255，越大去除越激进
     */
    fun removeFlatBackground(src: Bitmap, tolerance: Int = 32): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val visited = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()

        val corners = intArrayOf(
            0, w - 1, (h - 1) * w, h * w - 1,
        )
        for (c in corners) {
            if (!visited[c]) {
                val refColor = out[c % w, c / w]
                floodFill(out, w, h, c, refColor, tolerance, visited, stack)
            }
        }
        return out
    }

    private fun floodFill(
        bmp: Bitmap,
        w: Int,
        h: Int,
        start: Int,
        ref: Int,
        tol: Int,
        visited: BooleanArray,
        stack: ArrayDeque<Int>,
    ) {
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            if (visited[idx]) continue
            visited[idx] = true
            val x = idx % w
            val y = idx / w
            val color = bmp[x, y]
            if (!colorClose(color, ref, tol)) continue
            bmp[x, y] = Color.TRANSPARENT
            if (x > 0) stack.addLast(idx - 1)
            if (x < w - 1) stack.addLast(idx + 1)
            if (y > 0) stack.addLast(idx - w)
            if (y < h - 1) stack.addLast(idx + w)
        }
    }

    private fun colorClose(a: Int, b: Int, tol: Int): Boolean {
        val dr = abs(Color.red(a) - Color.red(b))
        val dg = abs(Color.green(a) - Color.green(b))
        val db = abs(Color.blue(a) - Color.blue(b))
        // 欧氏距离阈值（tol 放大到 RGB 空间）
        return sqrt((dr * dr + dg * dg + db * db).toDouble()) <= tol * 1.7
    }

    /**
     * 精灵归一化：裁到不透明像素包围盒（加 padding）→ 缩放到固定正方形画布。
     * 确保同一宠物各动作帧在悬浮窗里大小/锚点一致，切换动作不"跳"。
     */
    fun normalizeSprite(src: Bitmap, outputSize: Int = 512): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        for (i in pixels.indices) {
            if (pixels[i] ushr 24 > 10) {
                val x = i % w; val y = i / w
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (minX > maxX) return src // 全透明，原样返回

        val pad = (((maxX - minX) + (maxY - minY)) / 2 * 0.12f).toInt().coerceAtLeast(8)
        val x0 = (minX - pad).coerceAtLeast(0)
        val y0 = (minY - pad).coerceAtLeast(0)
        val x1 = (maxX + pad).coerceAtMost(w - 1)
        val y1 = (maxY + pad).coerceAtMost(h - 1)
        val cropped = Bitmap.createBitmap(src, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
        return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
    }

    /** 在 (cx,cy) 处以 radius 像素半径擦成透明（手动橡皮擦）。坐标为图像像素坐标。 */
    fun erase(bmp: Bitmap, cx: Int, cy: Int, radius: Int) {
        val r2 = radius * radius
        val x0 = (cx - radius).coerceAtLeast(0)
        val x1 = (cx + radius).coerceAtMost(bmp.width - 1)
        val y0 = (cy - radius).coerceAtLeast(0)
        val y1 = (cy + radius).coerceAtMost(bmp.height - 1)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) bmp[x, y] = Color.TRANSPARENT
            }
        }
    }
}
