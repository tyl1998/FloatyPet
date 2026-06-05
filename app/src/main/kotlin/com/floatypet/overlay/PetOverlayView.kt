package com.floatypet.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.floatypet.overlay.behavior.PetBehaviorEngine
import com.floatypet.overlay.render.PetRenderer
import kotlin.math.abs

/**
 * 悬浮窗宿主 View。
 * - 绘制委托给 [PetRenderer]（渲染隔离 CLAUDE.md §5.3）
 * - 触摸：单击 → [onTap]（运动引擎弹跳），双击 → 开心，拖拽 → [onDrag]（绝对坐标）
 * - 运动由 OverlayService 每帧驱动 PetMotionEngine 控制，View 只汇报触摸事件
 */
@SuppressLint("ViewConstructor")
class PetOverlayView(
    context: Context,
    private val renderer: PetRenderer,
    private val behaviorEngine: PetBehaviorEngine,
    private val onDragStart: () -> Unit,
    /** 拖拽中：传入手指在屏幕上的原始坐标（rawX, rawY），Service 计算宠物位置 */
    private val onDrag: (rawX: Float, rawY: Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onTap: () -> Unit,
) : View(context) {

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private var dragStarted = false
    private val touchSlop = 14f * resources.displayMetrics.density

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTap()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                renderer.playAction(behaviorEngine.onDoubleTap().action)
                invalidate()
                return true
            }
        })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer.setViewport(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        renderer.drawTo(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                dragStarted = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    if (!dragStarted) {
                        dragStarted = true
                        onDragStart()
                    }
                }
                if (dragging) onDrag(event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onDragEnd()
                    return true
                }
            }
        }
        return gestureDetector.onTouchEvent(event) || true
    }
}
