package com.floatypet.core.model

/**
 * 悬浮宠物的运行时状态。与渲染维度（2D/3D）无关，是悬浮窗的单一可信状态源。
 */
data class PetState(
    /** 悬浮窗左上角 X（px） */
    val x: Int = 0,
    /** 悬浮窗左上角 Y（px） */
    val y: Int = 0,
    /** 显示缩放比例 */
    val scale: Float = 1f,
    /** 透明度 0..1 */
    val alpha: Float = 1f,
    /** 当前正在播放的动作 */
    val currentAction: PetAction = PetAction.IDLE,
    /** 是否处于静默状态（免打扰时段/黑名单 APP/通话中等） */
    val silent: Boolean = false,
    /** 是否被临时隐藏 */
    val hidden: Boolean = false,
)
