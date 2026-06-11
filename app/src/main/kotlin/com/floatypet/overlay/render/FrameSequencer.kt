package com.floatypet.overlay.render

import android.graphics.Bitmap
import com.floatypet.core.model.PetAction

/**
 * 逐帧播放器。按动作存帧序列，按时间推进切帧。与 [PetRenderer] 解耦（CLAUDE.md §6.2）。
 *
 * 帧 Bitmap 由调用方（渲染器）传入；本类只管「当前该显示哪一帧」。
 * 非当前动作的帧可通过 [trimMemory] 回收（性能红线 CLAUDE.md §7）。
 */
class FrameSequencer {

    /** 帧播放模式。 */
    enum class PlayMode {
        /** 正向循环（idle / walk） */
        LOOP,

        /** 正向单次，播完停在末帧（greet / shrink） */
        ONCE,

        /** 乒乓循环：正向播完反向回（stretch） */
        PING_PONG,
    }

    private class Track(
        val frames: List<Bitmap>,
        val frameIntervalMs: Long,
        val mode: PlayMode,
    )

    private val tracks = mutableMapOf<PetAction, Track>()
    private var current: PetAction = PetAction.IDLE
    private var trackStartNanos = 0L

    /** 注册某动作的帧序列与播放参数。空列表表示移除该动作。 */
    fun setFrames(
        action: PetAction,
        frames: List<Bitmap>,
        frameIntervalMs: Long = 200L,
        mode: PlayMode = PlayMode.LOOP,
    ) {
        if (frames.isEmpty()) {
            tracks.remove(action)
        } else {
            tracks[action] = Track(frames, frameIntervalMs.coerceAtLeast(16L), mode)
        }
    }

    fun hasFrames(action: PetAction): Boolean = tracks[action]?.frames?.isNotEmpty() == true

    /** 始终返回 IDLE 轨道当前帧（用于无当前动作帧时降级）。 */
    fun idleFrame(frameTimeNanos: Long): Bitmap? {
        val saved = current
        current = PetAction.IDLE
        val frame = currentFrame(frameTimeNanos)
        current = saved
        return frame
    }

    /** 切换当前动作并重置帧游标。 */
    fun play(action: PetAction) {
        current = action
        trackStartNanos = 0L
    }

    /**
     * 返回当前应显示的帧。无帧返回 null（调用方走整体变换回退）。
     */
    fun currentFrame(frameTimeNanos: Long): Bitmap? {
        val track = tracks[current] ?: return null
        if (track.frames.size == 1) return track.frames[0]
        if (trackStartNanos == 0L) trackStartNanos = frameTimeNanos

        val elapsedMs = (frameTimeNanos - trackStartNanos) / 1_000_000L
        val step = (elapsedMs / track.frameIntervalMs).toInt()
        val n = track.frames.size
        val idx = when (track.mode) {
            PlayMode.LOOP -> step % n
            PlayMode.ONCE -> step.coerceAtMost(n - 1)
            PlayMode.PING_PONG -> {
                val period = 2 * (n - 1)
                val p = step % period
                if (p < n) p else period - p
            }
        }
        return track.frames[idx]
    }

    /** 回收非当前动作的 Bitmap。 */
    fun trimMemory() {
        val keep = current
        tracks.keys.filter { it != keep }.toList().forEach { action ->
            tracks[action]?.frames?.forEach { if (!it.isRecycled) it.recycle() }
            tracks.remove(action)
        }
    }

    /** 释放全部帧。 */
    fun disposeAll() {
        tracks.values.forEach { t -> t.frames.forEach { if (!it.isRecycled) it.recycle() } }
        tracks.clear()
    }
}
