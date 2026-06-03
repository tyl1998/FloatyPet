package com.floatypet.overlay.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Surface
import com.floatypet.core.model.PetAction
import com.floatypet.core.model.PetAssetType
import com.floatypet.core.model.PetBodyPart
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

    private val sequencer = FrameSequencer()

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

    // 临时动作（非 idle）的剩余展示时间
    private var transientUntilSec = 0f

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB74D") }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A2E2A") }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A65"); alpha = 120 }

    override fun init(surface: Surface, width: Int, height: Int) {
        // 2D 路径不使用 Surface（用 drawTo 画到宿主 View 的 Canvas）。
        setViewport(width, height)
    }

    override fun setViewport(width: Int, height: Int) {
        viewW = width
        viewH = height
    }

    override fun loadPet(assetPath: String, type: PetAssetType) {
        require(type == PetAssetType.SPRITE_2D) { "SpriteRenderer 仅支持 2D 精灵图" }
        // TODO: 扫描 assetPath 下各动作目录，注册到 FrameSequencer（懒加载）
    }

    override fun playAction(action: PetAction) {
        currentAction = action
        sequencer.play(action)
        // 非 idle 的临时动作展示 1.2s 后回到 idle
        transientUntilSec = if (action == PetAction.IDLE) 0f else elapsedSec + 1.2f
    }

    override fun render(frameTimeNanos: Long) {
        if (startNanos == 0L) startNanos = frameTimeNanos
        elapsedSec = (frameTimeNanos - startNanos) / 1_000_000_000f

        // 临时动作到期 → 回 idle
        if (transientUntilSec > 0f && elapsedSec >= transientUntilSec) {
            transientUntilSec = 0f
            currentAction = PetAction.IDLE
        }

        // idle 呼吸：轻微缩放起伏
        scale = if (currentAction == PetAction.IDLE) {
            1f + 0.03f * sin(elapsedSec * 2f)
        } else {
            // 临时动作给个简单的反馈位移/缩放，便于肉眼确认触摸生效
            when (currentAction) {
                PetAction.GREET -> 1.08f
                PetAction.SHRINK -> 0.88f
                PetAction.HAPPY -> 1f + 0.06f * sin(elapsedSec * 16f)
                else -> 1f
            }
        }
    }

    override fun drawTo(canvas: Canvas) {
        if (viewW == 0 || viewH == 0) return
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
        this.scale = scale
        this.dx = dx
        this.dy = dy
        this.rotation = rotation
    }

    override fun dispose() {
        sequencer.trimMemory()
    }
}
