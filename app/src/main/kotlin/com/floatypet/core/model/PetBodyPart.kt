package com.floatypet.core.model

/**
 * 触摸命中区域。2D 按精灵图纵向 1/3 分区命中，3D 按骨骼蒙皮命中，语义统一。
 * 见 AGENT.md §2.3 / §4.1。
 */
enum class PetBodyPart {
    /** 上 1/3：头部 */
    HEAD,

    /** 中 1/3：身体 */
    BODY,

    /** 下 1/3：腹部 */
    BELLY,
}
