package com.floatypet.core.model

/**
 * 宠物素材类型。MVP 仅 [SPRITE_2D]，预留 [MODEL_3D]（见 AGENT.md §4）。
 */
enum class PetAssetType {
    /** 2D 逐帧精灵图（按动作分目录的 PNG 序列） */
    SPRITE_2D,

    /** 3D 模型（glTF 2.0），V1.1+ */
    MODEL_3D,
}
