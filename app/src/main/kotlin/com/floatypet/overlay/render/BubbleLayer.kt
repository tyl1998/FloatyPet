package com.floatypet.overlay.render

import com.floatypet.core.model.BubbleContent

/**
 * 气泡对话层。悬浮窗除 [PetRenderer] 外另持有一个 [BubbleLayer]。
 * 渲染顺序固定两段式：先 PetRenderer 渲染宠物 → 再 BubbleLayer 渲染气泡（气泡永远在上）。
 * 见 AGENT.md §4.5 / CLAUDE.md §6.3。
 */
interface BubbleLayer {
    /** 展示气泡。 */
    fun show(content: BubbleContent)

    /** 立即收起。 */
    fun dismiss()

    /** 当前是否正在展示气泡。 */
    fun isShowing(): Boolean
}

/**
 * MVP 空实现：不渲染任何东西、不占内存。
 * 行为引擎输出的 PetResponse.bubble 恒为 null，由它消费，无副作用。
 * V1.1 替换为 TextBubbleLayer（Canvas 绘制气泡框，不走系统通知）。
 */
class NoOpBubbleLayer : BubbleLayer {
    override fun show(content: BubbleContent) = Unit
    override fun dismiss() = Unit
    override fun isShowing(): Boolean = false
}
