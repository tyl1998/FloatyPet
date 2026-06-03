package com.floatypet.overlay.render

import android.graphics.Bitmap
import com.floatypet.core.model.PetAction

/**
 * 逐帧播放器。负责按动作目录取帧、按间隔切换、支持三种播放模式。
 * 与 [PetRenderer] 解耦——替换渲染器不应影响帧播放逻辑（见 CLAUDE.md §6.2）。
 *
 * 骨架实现，方法体待补。帧 Bitmap **按动作懒加载、非当前动作及时回收**（性能红线，CLAUDE.md §7）。
 */
class FrameSequencer {

    /** 帧播放模式。 */
    enum class PlayMode {
        /** 正向循环（idle / walk） */
        LOOP,

        /** 正向单次，播完停在末帧并回调（greet / shrink） */
        ONCE,

        /** 乒乓循环：正向播完反向回（stretch） */
        PING_PONG,
    }

    /** 注册某动作的帧序列与播放参数。 */
    fun setFrames(
        action: PetAction,
        frames: List<Bitmap>,
        frameIntervalMs: Long = 200L,
        mode: PlayMode = PlayMode.LOOP,
    ) {
        TODO("注册帧序列：缓存元数据，Bitmap 懒加载")
    }

    /** 切换到某动作。 */
    fun play(action: PetAction) {
        TODO("切换当前动作并重置帧游标")
    }

    /**
     * 推进到下一帧并返回当前应显示的帧。
     * @param frameTimeNanos vsync 时间戳，用于按 frameIntervalMs 控制切帧
     * @return 当前帧 Bitmap；若该动作无帧则返回 null（由调用方走整体变换回退）
     */
    fun currentFrame(frameTimeNanos: Long): Bitmap? {
        TODO("根据时间戳推进帧游标，返回当前帧")
    }

    /** 回收非当前动作的 Bitmap。 */
    fun trimMemory() {
        TODO("释放非当前动作的帧 Bitmap")
    }
}
