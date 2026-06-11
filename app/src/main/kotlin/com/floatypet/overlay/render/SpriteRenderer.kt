package com.floatypet.overlay.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Surface
import com.floatypet.asset.edit.ImageOps
import com.floatypet.core.model.PetAction
import com.floatypet.core.model.PetAssetType
import com.floatypet.core.model.PetBodyPart
import java.io.File
import kotlin.math.sin

/**
 * MVP 2D 渲染器：基于 Canvas 自绘 + [FrameSequencer] 逐帧播放。
 * 不引入 Spine/Lottie（CLAUDE.md §2 / §9）。
 *
 * 当前阶段：尚无真实精灵图素材，先绘制一个**占位猫脸**以打通
 * 「悬浮窗 → 触摸 → 行为引擎 → 渲染」全链路。接入素材后，
 * [drawTo] 改为绘制 [FrameSequencer] 的当前帧。
 */
class SpriteRenderer : PetRenderer {

    private data class BoneRegion(
        val id: String,
        val l: Float, val t: Float, val r: Float, val b: Float,
        val pivotAtBottom: Boolean,
    )
    private data class RegionAnim(
        val dx: Float = 0f, val dy: Float = 0f,
        val rotation: Float = 0f, val scaleY: Float = 1f,
    )

    private val sequencer = FrameSequencer()
    private companion object {
        const val TAG = "SpriteRenderer"

        fun actionPlayParams(action: PetAction): Pair<Long, FrameSequencer.PlayMode> = when (action) {
            PetAction.IDLE    -> 400L to FrameSequencer.PlayMode.PING_PONG
            PetAction.SLEEP   -> 600L to FrameSequencer.PlayMode.PING_PONG
            PetAction.WALK    -> 150L to FrameSequencer.PlayMode.LOOP
            PetAction.GREET   -> 200L to FrameSequencer.PlayMode.ONCE
            PetAction.SHRINK  -> 180L to FrameSequencer.PlayMode.ONCE
            PetAction.STRETCH -> 300L to FrameSequencer.PlayMode.PING_PONG
            PetAction.HAPPY   -> 120L to FrameSequencer.PlayMode.LOOP
            PetAction.SAD     -> 500L to FrameSequencer.PlayMode.PING_PONG
            PetAction.SIT     -> 400L to FrameSequencer.PlayMode.PING_PONG
        }
    }

    private var viewW = 0
    private var viewH = 0
    private var currentAction = PetAction.IDLE

    // 整体变换（呼吸/回退用）
    private var scale = 1f
    private var dx = 0f
    private var dy = 0f
    private var rotation = 0f

    // 动画时间基准
    private var startNanos = 0L
    private var elapsedSec = 0f
    private var lastFrameNanos = 0L

    // 临时动作（非 idle）的剩余展示时间
    private var transientUntilSec = 0f
    private var mirrorX = false
    private var walkPhasePx = 0f
    private var boneMode = false

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 0, 0, 0) }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB74D") }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A2E2A") }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A65"); alpha = 120 }
    private val defaultRig = listOf(
        BoneRegion("lower_body", 0.05f, 0.55f, 0.95f, 0.82f, false),
        BoneRegion("upper_body", 0.05f, 0.22f, 0.95f, 0.62f, true),
        BoneRegion("left_paw",   0.00f, 0.70f, 0.50f, 1.00f, false),
        BoneRegion("right_paw",  0.50f, 0.70f, 1.00f, 1.00f, false),
        BoneRegion("head",       0.12f, 0.00f, 0.88f, 0.32f, true),
    )

    override fun init(surface: Surface, width: Int, height: Int) {
        // 2D 路径不使用 Surface（用 drawTo 画到宿主 View 的 Canvas）。
        setViewport(width, height)
    }

    override fun setViewport(width: Int, height: Int) {
        viewW = width
        viewH = height
    }

    private var loaded = false

    override fun loadPet(assetPath: String, type: PetAssetType) {
        require(type == PetAssetType.SPRITE_2D) { "SpriteRenderer 仅支持 2D 精灵图" }
        loaded = false
        sequencer.disposeAll()
        if (assetPath.isBlank()) {
            Log.w(TAG, "loadPet: assetPath is blank, will use placeholder")
            return
        }
        val petDir = File(assetPath)
        Log.d(TAG, "loadPet: scanning $assetPath exists=${petDir.exists()}")
        var totalFrames = 0
        var idleFrames = 0
        for (action in PetAction.entries) {
            val dir = File(petDir, action.assetDir)
            if (!dir.exists()) continue
            val files = dir.listFiles { f -> f.extension.equals("png", true) }
                ?.sortedBy { it.name } ?: continue
            val decodeOpts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val frames = files.mapNotNull { f ->
                val bmp = BitmapFactory.decodeFile(f.absolutePath, decodeOpts)
                if (bmp == null) {
                    Log.e(TAG, "loadPet: decodeFile null for ${f.name}")
                    return@mapNotNull null
                }
                // Diagnose background transparency
                val tl = bmp.getPixel(0, 0)
                val tr = bmp.getPixel(bmp.width - 1, 0)
                val bl = bmp.getPixel(0, bmp.height - 1)
                val br = bmp.getPixel(bmp.width - 1, bmp.height - 1)
                val cornerAlphas = intArrayOf(Color.alpha(tl), Color.alpha(tr), Color.alpha(bl), Color.alpha(br))
                Log.d(TAG, "loadPet: ${f.name} ${bmp.width}x${bmp.height} config=${bmp.config} cornerAlpha=${cornerAlphas.toList()}")

                if (cornerAlphas.all { it == 255 }) {
                    // All corners opaque → background was not removed at import time; attempt here.
                    // Full-edge seeding in removeFlatBackground handles non-uniform backgrounds.
                    // Low tolerance (20) avoids eating into the pet's own colours.
                    Log.i(TAG, "loadPet: ${action.name}/${f.name} opaque corners — auto-removing (tol=20)")
                    ImageOps.removeFlatBackground(bmp, tolerance = 20)
                } else {
                    bmp
                }
            }
            if (frames.isNotEmpty()) {
                val (intervalMs, mode) = actionPlayParams(action)
                sequencer.setFrames(action, frames, intervalMs, mode)
                totalFrames += frames.size
                if (action == PetAction.IDLE) idleFrames = frames.size
            }
        }
        // boneMode = true only when NO real frames exist at all (pure placeholder).
        // Even a single AI-generated frame must use drawFrame() — drawRigged() splits
        // the image into independent bone regions and makes limbs move unnaturally.
        boneMode = totalFrames == 0
        Log.d(TAG, "loadPet done: totalFrames=$totalFrames boneMode=$boneMode")
        sequencer.play(PetAction.IDLE)
        loaded = totalFrames > 0
    }

    override fun playAction(action: PetAction) {
        currentAction = action
        sequencer.play(action)
        transientUntilSec = if (action == PetAction.IDLE) 0f else elapsedSec + 1.2f
    }

    override fun setWalkPhase(distancePx: Float) { walkPhasePx = distancePx }

    override fun render(frameTimeNanos: Long) {
        lastFrameNanos = frameTimeNanos
        if (startNanos == 0L) startNanos = frameTimeNanos
        elapsedSec = (frameTimeNanos - startNanos) / 1_000_000_000f

        // 临时动作到期 → 回 idle
        if (transientUntilSec > 0f && elapsedSec >= transientUntilSec) {
            transientUntilSec = 0f
            currentAction = PetAction.IDLE
        }

        rotation = 0f
        when (currentAction) {
            PetAction.IDLE -> {
                // Very subtle breathing: scale ±1.5%, float ±2px — stays visually "still"
                val a = sin(elapsedSec * 1.9f)
                val b = sin(elapsedSec * 3.1f)
                scale = 1f + 0.015f * a + 0.005f * b
                dy = -2f * a - 0.5f * b
            }
            PetAction.SLEEP -> {
                // Slow deep breathing only
                val a = sin(elapsedSec * 0.8f)
                scale = 1f + 0.02f * a
                dy = -1.5f * a
            }
            PetAction.SIT -> {
                // Sitting: barely any movement, just alive
                val a = sin(elapsedSec * 1.5f)
                scale = 1f + 0.01f * a
                dy = -1f * a
            }
            PetAction.WALK -> {
                // Body-bob tied to actual displacement — works well with real walk frames
                val step = sin(walkPhasePx / 55f)
                val stepAbs = kotlin.math.abs(step)
                scale = 1f + 0.04f * stepAbs
                dy = -6f * stepAbs
                rotation = 3f * step
            }
            PetAction.HAPPY -> {
                // Bounce up and down — semantically correct for "happy"
                val t = kotlin.math.abs(sin(elapsedSec * 8f))
                scale = 1f + 0.10f * t
                dy = -12f * t
            }
            PetAction.GREET -> {
                // Gentle forward lean / nod
                val t = sin(elapsedSec * 6f)
                scale = 1f + 0.03f * kotlin.math.abs(t)
                dy = -4f * kotlin.math.abs(t)
                rotation = 3f * t
            }
            PetAction.SHRINK -> {
                // Compress down — scale shrinks, no lateral movement
                scale = 0.88f - 0.03f * kotlin.math.abs(sin(elapsedSec * 6f))
                dy = 4f * kotlin.math.abs(sin(elapsedSec * 6f))
            }
            PetAction.STRETCH -> {
                // Stretch tall then relax
                val t = sin(elapsedSec * 1.5f)
                scale = 1f + 0.08f * t
                dy = -8f * t
            }
            PetAction.SAD -> {
                // Slow droop
                val a = sin(elapsedSec * 1.2f)
                scale = 1f - 0.02f * kotlin.math.abs(a)
                dy = 3f * kotlin.math.abs(a)
            }
        }
    }

    override fun drawTo(canvas: Canvas) {
        if (viewW == 0 || viewH == 0) return
        if (!loaded) return
        val frame = sequencer.currentFrame(lastFrameNanos)
            ?: sequencer.idleFrame(lastFrameNanos)
        if (frame != null && !frame.isRecycled) {
            val fit = minOf(viewW.toFloat() / frame.width, viewH.toFloat() / frame.height)
            if (boneMode) {
                val cx = viewW / 2f + dx
                drawShadow(canvas, cx, viewH / 2f + frame.height * fit / 2f + 2f, frame.width * fit)
                drawRigged(canvas, frame)
            } else {
                val w = frame.width * fit * scale
                val h = frame.height * fit * scale
                val cx = viewW / 2f + dx
                val cy = viewH / 2f + dy
                drawShadow(canvas, cx, cy + h / 2f + 2f, w)
                drawFrame(canvas, frame)
            }
        } else {
            drawPlaceholderCat(canvas)
        }
    }

    private fun drawFrame(canvas: Canvas, frame: Bitmap) {
        val cx = viewW / 2f + dx
        val cy = viewH / 2f + dy
        val fit = minOf(viewW.toFloat() / frame.width, viewH.toFloat() / frame.height)
        val w = frame.width * fit * scale
        val h = frame.height * fit * scale

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        if (mirrorX) canvas.scale(-1f, 1f, cx, cy)

        canvas.drawBitmap(
            frame,
            Rect(0, 0, frame.width, frame.height),
            RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f),
            framePaint,
        )

        canvas.restore()
    }

    private fun drawShadow(canvas: Canvas, cx: Float, bottomY: Float, petW: Float) {
        val rx = petW * 0.28f
        canvas.drawOval(RectF(cx - rx, bottomY - 5f, cx + rx, bottomY + 5f), shadowPaint)
    }

    private fun drawRigged(canvas: Canvas, frame: Bitmap) {
        val cx = viewW / 2f + dx
        val cy = viewH / 2f
        val fit = minOf(viewW.toFloat() / frame.width, viewH.toFloat() / frame.height)
        val imgW = frame.width * fit
        val imgH = frame.height * fit
        val baseX = cx - imgW / 2f
        val baseY = cy - imgH / 2f

        canvas.save()
        if (mirrorX) canvas.scale(-1f, 1f, cx, cy)

        for (region in defaultRig) {
            val anim = regionAnim(region.id)
            val srcRect = Rect(
                (region.l * frame.width).toInt(),
                (region.t * frame.height).toInt(),
                (region.r * frame.width).toInt(),
                (region.b * frame.height).toInt(),
            )
            val rL = baseX + region.l * imgW + anim.dx
            val rT = baseY + region.t * imgH + anim.dy
            val rR = baseX + region.r * imgW + anim.dx
            val rB = baseY + region.b * imgH + anim.dy
            val pivotX = (rL + rR) / 2f
            val pivotY = if (region.pivotAtBottom) rB else rT

            canvas.save()
            if (anim.rotation != 0f) canvas.rotate(anim.rotation, pivotX, pivotY)
            if (anim.scaleY != 1f) canvas.scale(1f, anim.scaleY, pivotX, pivotY)
            canvas.drawBitmap(frame, srcRect, RectF(rL, rT, rR, rB), framePaint)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun regionAnim(id: String): RegionAnim = when (currentAction) {
        PetAction.IDLE   -> idleRegionAnim(id)
        PetAction.WALK   -> walkRegionAnim(id)
        PetAction.SLEEP  -> sleepRegionAnim(id)
        PetAction.HAPPY  -> happyRegionAnim(id)
        PetAction.GREET  -> greetRegionAnim(id)
        PetAction.SHRINK -> shrinkRegionAnim(id)
        else -> RegionAnim()
    }

    private fun idleRegionAnim(id: String): RegionAnim {
        val a = sin(elapsedSec * 1.9f)
        val b = sin(elapsedSec * 3.1f)
        return when (id) {
            "head"       -> RegionAnim(dy = -4f * a, rotation = 1.5f * a + 0.6f * b)
            "upper_body" -> RegionAnim(dy = -2f * a, scaleY = 1f + 0.04f * a)
            "lower_body" -> RegionAnim(
                dy = -1f * sin(elapsedSec * 1.9f - 0.4f),
                scaleY = 1f + 0.02f * sin(elapsedSec * 1.9f - 0.4f),
            )
            "left_paw"   -> RegionAnim(dy = -2f * a)
            "right_paw"  -> RegionAnim(dy = -2f * sin(elapsedSec * 1.9f + 0.3f))
            else         -> RegionAnim()
        }
    }

    private fun walkRegionAnim(id: String): RegionAnim {
        val step = sin(walkPhasePx / 55f)
        val stepAbs = kotlin.math.abs(step)
        return when (id) {
            "head"       -> RegionAnim(dy = -6f * stepAbs, rotation = 4f * step)
            "upper_body" -> RegionAnim(dy = -3f * stepAbs, rotation = 2.5f * step)
            "lower_body" -> RegionAnim(dy = -1.5f * stepAbs)
            "left_paw"   -> RegionAnim(
                dy = -14f * kotlin.math.max(0f, step),
                rotation = -22f * kotlin.math.max(0f, step),
            )
            "right_paw"  -> RegionAnim(
                dy = 14f * kotlin.math.min(0f, step),
                rotation = 22f * kotlin.math.min(0f, step),
            )
            else         -> RegionAnim()
        }
    }

    private fun sleepRegionAnim(id: String): RegionAnim {
        val a = sin(elapsedSec * 1.1f)
        return when (id) {
            "head"       -> RegionAnim(rotation = 8f + 2f * a, dy = 4f + a)
            "upper_body" -> RegionAnim(scaleY = 1f + 0.02f * a)
            "left_paw"   -> RegionAnim(rotation = 5f)
            "right_paw"  -> RegionAnim(rotation = -5f)
            else         -> RegionAnim()
        }
    }

    private fun happyRegionAnim(id: String): RegionAnim {
        val t = sin(elapsedSec * 10f)
        val bounce = kotlin.math.abs(t)
        return when (id) {
            "head"       -> RegionAnim(dy = -15f * bounce, rotation = 8f * t)
            "upper_body" -> RegionAnim(dy = -10f * bounce, scaleY = 1f + 0.05f * bounce)
            "lower_body" -> RegionAnim(dy = -5f * bounce)
            "left_paw"   -> RegionAnim(dy = -8f * bounce, rotation = -20f * t)
            "right_paw"  -> RegionAnim(dy = -8f * bounce, rotation = 20f * t)
            else         -> RegionAnim()
        }
    }

    private fun greetRegionAnim(id: String): RegionAnim {
        val t = sin(elapsedSec * 7f)
        return when (id) {
            "head"       -> RegionAnim(dy = -5f * t)
            "upper_body" -> RegionAnim(rotation = 3f * t)
            "left_paw"   -> RegionAnim(dy = -20f - 5f * t, rotation = -30f - 5f * t)
            else         -> RegionAnim()
        }
    }

    private fun shrinkRegionAnim(id: String): RegionAnim {
        val t = kotlin.math.abs(sin(elapsedSec * 6f))
        return when (id) {
            "head"       -> RegionAnim(rotation = -3f * t, dy = 2f * t)
            "upper_body" -> RegionAnim(scaleY = 0.9f - 0.03f * t)
            "lower_body" -> RegionAnim(dy = 3f * t)
            "left_paw"   -> RegionAnim(rotation = 10f)
            "right_paw"  -> RegionAnim(rotation = -10f)
            else         -> RegionAnim()
        }
    }

    private fun drawPlaceholderCat(canvas: Canvas) {
        val cx = viewW / 2f + dx
        val cy = viewH / 2f + dy
        val r = minOf(viewW, viewH) * 0.36f * scale

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        // 脸
        canvas.drawCircle(cx, cy, r, facePaint)
        // 耳朵
        val ear = r * 0.5f
        canvas.drawCircle(cx - r * 0.62f, cy - r * 0.62f, ear * 0.6f, facePaint)
        canvas.drawCircle(cx + r * 0.62f, cy - r * 0.62f, ear * 0.6f, facePaint)
        // 眼睛（HAPPY 时眯成弯线，靠缩短高度模拟）
        val eyeR = r * 0.12f
        val eyeY = cy - r * 0.1f
        canvas.drawCircle(cx - r * 0.35f, eyeY, eyeR, darkPaint)
        canvas.drawCircle(cx + r * 0.35f, eyeY, eyeR, darkPaint)
        // 腮红
        canvas.drawCircle(cx - r * 0.55f, cy + r * 0.2f, r * 0.16f, blushPaint)
        canvas.drawCircle(cx + r * 0.55f, cy + r * 0.2f, r * 0.16f, blushPaint)
        // 嘴
        val mouth = RectF(cx - r * 0.18f, cy + r * 0.1f, cx + r * 0.18f, cy + r * 0.38f)
        canvas.drawArc(mouth, 20f, 140f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A2E2A"); style = Paint.Style.STROKE; strokeWidth = r * 0.05f
        })

        canvas.restore()
    }

    override fun hitTest(x: Float, y: Float): PetBodyPart? {
        if (viewW == 0 || viewH == 0) return null
        // 简单包围盒：以中心为圆的命中区域
        val cx = viewW / 2f + dx
        val cy = viewH / 2f + dy
        val r = minOf(viewW, viewH) * 0.45f
        val dxx = x - cx
        val dyy = y - cy
        if (dxx * dxx + dyy * dyy > r * r) return null
        // 纵向 1/3 分区
        return when {
            y < viewH / 3f -> PetBodyPart.HEAD
            y < viewH * 2f / 3f -> PetBodyPart.BODY
            else -> PetBodyPart.BELLY
        }
    }

    override fun applyTransform(scale: Float, dx: Float, dy: Float, rotation: Float) {
        this.scale = scale; this.dx = dx; this.dy = dy; this.rotation = rotation
    }

    override fun setMirrorX(mirror: Boolean) { mirrorX = mirror }

    override fun dispose() {
        loaded = false
        sequencer.disposeAll()
    }
}
