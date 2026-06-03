package com.floatypet.core.model

/**
 * 气泡对话内容。MVP 不渲染（由 NoOpBubbleLayer 消费），V1.1 由 TextBubbleLayer 渲染。
 * 见 AGENT.md §4.5。
 */
data class BubbleContent(
    /** 气泡文字 */
    val text: String,
    /** 气泡样式 */
    val style: BubbleStyle = BubbleStyle.NORMAL,
    /** 自动消失时长（毫秒） */
    val durationMs: Long = 3_000L,
    /** 相对宠物的方位 */
    val anchor: BubbleAnchor = BubbleAnchor.TOP,
)

/** 气泡视觉样式。 */
enum class BubbleStyle {
    /** 普通对话 */
    NORMAL,

    /** 提醒（如使用时长） */
    REMINDER,

    /** 情绪表达 */
    EMOTION,
}

/** 气泡相对宠物的方位。 */
enum class BubbleAnchor {
    TOP,
    LEFT,
    RIGHT,
}
