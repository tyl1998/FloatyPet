package com.floatypet.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.floatypet.core.model.PetResponse
import com.floatypet.overlay.behavior.PetBehaviorEngine
import com.floatypet.overlay.render.PetRenderer

/**
 * 悬浮窗宿主 View。职责：
 *  - 把绘制委托给 [PetRenderer]（自身不碰宠物像素，渲染隔离 CLAUDE.md §5.3）
 *  - 触摸手势识别（点击/双击/长按）→ [PetBehaviorEngine] → [PetResponse] → 播放动作
 *  - 拖拽移动悬浮窗（通过 [onDrag] 回调让 Service 更新 WindowManager 位置）
 *  - 松手边缘吸附（通过 [onDragEnd] 回调）
 *
 * 注：所有触发都经由 [PetBehaviorEngine] 输出 [PetResponse]，不绕过（CLAUDE.md §5.5）。
 */
@SuppressLint("ViewConstructor")
class PetOverlayView(
    context: Context,
    private val renderer: PetRenderer,
    private val behaviorEngine: PetBehaviorEngine,
    /** 拖拽中：传入相对按下点的位移，由 Service 更新窗口位置 */
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    /** 拖拽结束：触发边缘吸附 */
    private val onDragEnd: () -> Unit,
) : View(context) {

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private val touchSlop = 12f * resources.displayMetrics.density

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            dispatchTouchAt(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // 双击：蹭手（用 HAPPY 占位反馈）
            apply(behaviorEngine.onDoubleTap())
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // 长按进入拖拽态由 onTouchEvent 的位移判断处理，这里给个轻反馈
            performHapticFeedback(0)
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
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    onDrag(dx.toInt(), dy.toInt())
                    downRawX = event.rawX
                    downRawY = event.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onDragEnd()
                    return true // 拖拽结束不再当作点击
                }
            }
        }
        // 非拖拽时交给手势识别（点击/双击/长按）
        return gestureDetector.onTouchEvent(event) || true
    }

    /** 命中检测 → 行为引擎 → 播放动作。 */
    private fun dispatchTouchAt(x: Float, y: Float) {
        val part = renderer.hitTest(x, y) ?: return
        apply(behaviorEngine.onTouch(part))
    }

    private fun apply(response: PetResponse) {
        renderer.playAction(response.action)
        // response.bubble 在 MVP 恒为 null，由 NoOpBubbleLayer 消费（此处暂不接气泡层）
        invalidate()
    }
}
