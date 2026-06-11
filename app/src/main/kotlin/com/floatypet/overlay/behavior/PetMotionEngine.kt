package com.floatypet.overlay.behavior

import com.floatypet.core.model.PetAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 宠物自主运动状态机。每帧由 OverlayService 调用 [tick]，返回当前运动状态。
 *
 * 状态：
 *  IDLE_STAND  → 随机切换到 WALK 或 IDLE_ACTION
 *  WALK        → 朝某方向行走，碰到边界转身；随机停下
 *  IDLE_ACTION → 播放一个待机小动作（sit/stretch/happy），完毕回 IDLE_STAND
 *  JUMP        → 弹起后受重力下落，落地回 IDLE_STAND
 *  DRAGGED     → 外部拖拽中，引擎挂起
 *  FALLING     → 拖拽松手后自由落体到地面
 */
class PetMotionEngine {

    enum class MoveState { IDLE_STAND, WALK, IDLE_ACTION, JUMP, DRAGGED, FALLING }

    data class MotionFrame(
        val x: Float,               // 窗口左上角 X（px）
        val y: Float,               // 窗口左上角 Y（px）
        val action: PetAction,
        val facingRight: Boolean,   // false = 向左，SpriteRenderer 需水平翻转
    )

    // ── 屏幕/窗口尺寸（由 OverlayService 设置）────────────────
    var screenW: Int = 1080
    var screenH: Int = 2400
    var petSize: Int = 280          // 悬浮窗边长 px

    // ── 当前位置 ──────────────────────────────────────────────
    var x: Float = 100f
    var y: Float = 0f               // 初始化时贴地

    // ── 状态 ──────────────────────────────────────────────────
    var state: MoveState = MoveState.IDLE_STAND
        private set

    private var facingRight: Boolean = true
    private var walkSpeedPx: Float = 4f         // px/帧
    private var stateTimer: Int = 0             // 帧计数
    private var stateDuration: Int = 60         // 当前状态持续帧数
    private var idleAction: PetAction = PetAction.IDLE

    // 跳跃 / 重力
    private var vy: Float = 0f                  // 垂直速度（px/帧），向下为正
    private val gravity: Float = 1.8f
    private val jumpImpulse: Float = -28f
    private val groundY: Float get() = screenH - petSize - 80f  // 贴近底部导航栏上方

    // ── 外部接口 ──────────────────────────────────────────────

    /** 拖拽开始：挂起引擎。 */
    fun onDragStart() {
        state = MoveState.DRAGGED
        vy = 0f
    }

    /** 拖拽结束：以当前位置开始自由落体。 */
    fun onDragEnd() {
        state = MoveState.FALLING
        vy = 0f
    }

    /** 外部拖拽更新宠物位置。 */
    fun setPosition(newX: Float, newY: Float) {
        x = newX
        y = newY
    }

    /** 单击触摸 → 跳一下。 */
    fun onTap() {
        if (state == MoveState.DRAGGED || state == MoveState.FALLING) return
        state = MoveState.JUMP
        vy = jumpImpulse
    }

    /**
     * 每帧调用，返回本帧的运动状态。
     * @param dtFrames 通常传 1.0f；可按实际帧间隔缩放
     */
    fun tick(dtFrames: Float = 1f): MotionFrame {
        when (state) {
            MoveState.DRAGGED -> Unit  // 位置由外部 setPosition 控制
            MoveState.FALLING -> tickFalling(dtFrames)
            MoveState.JUMP -> tickJump(dtFrames)
            MoveState.WALK -> tickWalk(dtFrames)
            MoveState.IDLE_STAND -> tickIdleStand()
            MoveState.IDLE_ACTION -> tickIdleAction()
        }
        return MotionFrame(x, y, currentAction(), facingRight)
    }

    // ── 状态逻辑 ──────────────────────────────────────────────

    private fun tickFalling(dt: Float) {
        vy += gravity * dt
        y += vy * dt
        val ground = groundY
        if (y >= ground) {
            y = ground
            vy = 0f
            transitionTo(MoveState.IDLE_STAND, 40 + rand(60))
        }
    }

    private fun tickJump(dt: Float) {
        vy += gravity * dt
        y += vy * dt
        val ground = groundY
        if (y >= ground) {
            y = ground
            vy = 0f
            transitionTo(MoveState.IDLE_STAND, 20 + rand(30))
        }
    }

    private fun tickWalk(dt: Float) {
        val speed = walkSpeedPx * dt
        x += if (facingRight) speed else -speed

        val maxX = (screenW - petSize).toFloat()
        if (x <= 0f) { x = 0f; facingRight = true }
        if (x >= maxX) { x = maxX; facingRight = false }

        stateTimer++
        if (stateTimer >= stateDuration) {
            // 随机停下，或继续走
            if (Random.nextFloat() < 0.5f) {
                transitionTo(MoveState.IDLE_STAND, 40 + rand(80))
            } else {
                // 继续走，随机换向
                if (Random.nextFloat() < 0.3f) facingRight = !facingRight
                transitionTo(MoveState.WALK, 80 + rand(120))
            }
        }
    }

    private fun tickIdleStand() {
        stateTimer++
        if (stateTimer >= stateDuration) {
            val r = Random.nextFloat()
            when {
                // 20% walk — pet should mostly stay put, occasionally wander
                r < 0.20f -> {
                    facingRight = Random.nextBoolean()
                    transitionTo(MoveState.WALK, 80 + rand(160))
                }
                // 40% idle action (sit/stretch/happy) — expressive but stays in place
                r < 0.60f -> {
                    idleAction = randomIdleAction()
                    transitionTo(MoveState.IDLE_ACTION, actionDuration(idleAction))
                }
                // 40% just keep standing — gentle idle breathing
                else -> transitionTo(MoveState.IDLE_STAND, 120 + rand(180))
            }
        }
    }

    private fun tickIdleAction() {
        stateTimer++
        if (stateTimer >= stateDuration) {
            transitionTo(MoveState.IDLE_STAND, 20 + rand(50))
        }
    }

    private fun transitionTo(next: MoveState, duration: Int) {
        state = next
        stateDuration = duration
        stateTimer = 0
    }

    private fun currentAction(): PetAction = when (state) {
        MoveState.WALK -> PetAction.WALK
        MoveState.JUMP -> PetAction.HAPPY
        MoveState.FALLING -> PetAction.SHRINK
        MoveState.IDLE_ACTION -> idleAction
        else -> PetAction.IDLE
    }

    private fun randomIdleAction(): PetAction = when (rand(4)) {
        0 -> PetAction.SIT
        1 -> PetAction.STRETCH
        2 -> PetAction.HAPPY
        else -> PetAction.SLEEP
    }

    private fun actionDuration(action: PetAction): Int = when (action) {
        PetAction.SLEEP -> 150 + rand(200)
        PetAction.STRETCH -> 60 + rand(40)
        else -> 50 + rand(60)
    }

    private fun rand(n: Int) = Random.nextInt(max(1, n))

    /** 初始化：宠物贴地，屏幕中央。 */
    fun reset() {
        x = (screenW / 2 - petSize / 2).toFloat()
        y = groundY
        state = MoveState.IDLE_STAND
        stateTimer = 0
        stateDuration = 180 + rand(120)   // 3-5 s initial pause before any transition
        vy = 0f
        facingRight = true
    }
}
