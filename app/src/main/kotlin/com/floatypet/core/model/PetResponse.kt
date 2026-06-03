package com.floatypet.core.model

/**
 * 行为引擎的**统一输出模型**。所有触发场景（触摸 / 设备状态 / 天气 / 时间 / APP 分类）
 * 的行为规则都输出 [PetResponse]，不许某些场景直接调 renderer 绕过（见 CLAUDE.md §5.5）。
 *
 * MVP 阶段 [bubble] 恒为 null，由 NoOpBubbleLayer 消费、无副作用；
 * V1.1 引入气泡时只需让规则填充 [bubble]，行为引擎与触发逻辑零改动（见 AGENT.md §4.5）。
 */
data class PetResponse(
    /** 要播放的动作 */
    val action: PetAction,
    /** 可选气泡。MVP 恒为 null */
    val bubble: BubbleContent? = null,
)
