package com.floatypet.overlay.render

import android.graphics.Canvas
import android.view.Surface
import com.floatypet.core.model.PetAction
import com.floatypet.core.model.PetAssetType
import com.floatypet.core.model.PetBodyPart

/**
 * 渲染隔离层。悬浮窗、触摸交互、行为引擎**只能通过本接口与渲染打交道**，
 * 禁止直接操作 Bitmap/Canvas（见 CLAUDE.md §5.3）。这是 2D→3D 升级的隔离边界。
 *
 * MVP 实现：[SpriteRenderer]（逐帧精灵图）。
 * V1.1+ 预留：GLTFRenderer（基于 Filament / OpenGL ES）。
 */
interface PetRenderer {

    /**
     * 3D 路径初始化渲染目标（SurfaceView/GL）。
     * MVP 2D 路径不用 Surface（用 [drawTo] 直接画到宿主 View 的 Canvas），可空实现。
     */
    fun init(surface: Surface, width: Int, height: Int)

    /** 设置视口尺寸（宿主 View 尺寸变化时调用）。 */
    fun setViewport(width: Int, height: Int)

    /** 加载宠物素材。 */
    fun loadPet(assetPath: String, type: PetAssetType)

    /** 播放某个动作（idle 为默认循环，其余播完回到 idle）。 */
    fun playAction(action: PetAction)

    /**
     * 推进动画到指定时间戳。由渲染循环（Choreographer）按帧驱动。
     * 仅更新内部帧/变换状态，不直接绘制——绘制在 [drawTo]。
     */
    fun render(frameTimeNanos: Long)

    /**
     * 2D 路径：把当前帧绘制到宿主 View 提供的 Canvas。
     * 悬浮窗 View 在 onDraw 里调用本方法，自身不碰宠物像素（保持渲染隔离，CLAUDE.md §5.3）。
     */
    fun drawTo(canvas: Canvas)

    /**
     * 命中检测。2D 按精灵图纵向 1/3 分区，3D 按射线-蒙皮求交。
     * @return 命中的部位；未命中宠物返回 null。
     */
    fun hitTest(x: Float, y: Float): PetBodyPart?

    /** 整体变换（单帧回退 / 呼吸效果用）。 */
    fun applyTransform(scale: Float, dx: Float, dy: Float, rotation: Float)

    /** 同步行走距离（px），使步频与移动速度精确对齐。在 [render] 前调用。 */
    fun setWalkPhase(distancePx: Float) {}

    /** 水平镜像——向左走时传 true，右走传 false。 */
    fun setMirrorX(mirror: Boolean)

    /** 释放资源。 */
    fun dispose()
}
