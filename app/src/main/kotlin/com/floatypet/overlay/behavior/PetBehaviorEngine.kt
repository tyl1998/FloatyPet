package com.floatypet.overlay.behavior

import com.floatypet.core.model.PetAction
import com.floatypet.core.model.PetBodyPart
import com.floatypet.core.model.PetResponse

/**
 * 行为引擎：把所有触发场景映射为统一的 [PetResponse]。
 *
 * 设计铁律（CLAUDE.md §5.5）：触摸 / 设备状态 / 天气 / 时间 / APP 分类等所有触发，
 * 都必须经由本引擎输出 [PetResponse]，不许绕过直接调 renderer。
 * MVP 阶段 [PetResponse.bubble] 恒为 null；V1.1 接气泡时只在这里填 bubble 字段。
 *
 * 骨架实现，规则待补。
 */
class PetBehaviorEngine {

    /** 单击某部位时的反应。 */
    fun onTouch(part: PetBodyPart): PetResponse = when (part) {
        PetBodyPart.HEAD -> PetResponse(PetAction.GREET)
        PetBodyPart.BODY -> PetResponse(PetAction.HAPPY)
        PetBodyPart.BELLY -> PetResponse(PetAction.SHRINK)
    }

    /** 双击：蹭手（用 HAPPY 占位）。 */
    fun onDoubleTap(): PetResponse = PetResponse(PetAction.HAPPY)

    /** 设备/环境状态变化时的反应（充电、低电、解锁、天气、时间、APP 分类等）。 */
    fun onStateChanged(trigger: BehaviorTrigger): PetResponse {
        TODO("根据 trigger 查规则表，返回 PetResponse（MVP bubble 恒为 null）")
    }
}

/**
 * 行为触发源。后续按 AGENT.md §2.4 / §3.2 扩充（充电/低电/解锁/无操作/切回桌面/
 * 天气变化/时间段/APP 分类）。
 */
sealed interface BehaviorTrigger {
    data object Unlocked : BehaviorTrigger
    data object PowerConnected : BehaviorTrigger
    data object BatteryLow : BehaviorTrigger
    data object IdleTimeout : BehaviorTrigger

    /** 天气变化（V1 基础层，无需权限） */
    data class WeatherChanged(val condition: String) : BehaviorTrigger

    /** 前台 APP 分类变化（需 Usage Access） */
    data class ForegroundAppCategory(val category: String) : BehaviorTrigger
}
