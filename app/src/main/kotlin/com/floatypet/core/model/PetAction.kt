package com.floatypet.core.model

/**
 * 宠物动作语义。2D（逐帧精灵图）与 3D（骨骼动画）渲染器共用同一套契约，
 * 业务层禁止使用字符串/魔法数表达动作（见 CLAUDE.md §5.4）。
 *
 * 每个动作对应素材目录下一个同名（小写）子目录的帧序列，例如 IDLE -> `idle/`。
 */
enum class PetAction {
    /** 待机：默认循环动作，始终在播放 */
    IDLE,

    /** 走路 */
    WALK,

    /** 睡觉 */
    SLEEP,

    /** 趴下/坐 */
    SIT,

    /** 伸懒腰 */
    STRETCH,

    /** 迎接/打招呼 */
    GREET,

    /** 身体收缩（被戳腹部/躲避） */
    SHRINK,

    /** 开心 */
    HAPPY,

    /** 委屈/低落 */
    SAD,
    ;

    /** 素材子目录名（小写）。 */
    val assetDir: String get() = name.lowercase()

    companion object {
        /** AI 生成的核心动作（第一阶段优先生成，见 AGENT.md §1.2 路径 B）。 */
        val CORE = listOf(IDLE, GREET, SLEEP)

        /** 后台补全的其余动作。 */
        val SECONDARY = entries - CORE.toSet()
    }
}
