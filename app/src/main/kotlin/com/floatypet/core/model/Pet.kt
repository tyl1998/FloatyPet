package com.floatypet.core.model

/**
 * 一只宠物的元数据。帧文件按动作存于 `pet_data/{id}/{action}/frame_xx.png`，
 * 本模型只描述「有哪些动作可用 + 基本信息」，不持有 Bitmap。
 */
data class Pet(
    val id: String,
    val name: String,
    val assetType: PetAssetType = PetAssetType.SPRITE_2D,
    /** 已具备帧素材的动作集合。MVP 首切片通常只有 IDLE。 */
    val availableActions: Set<PetAction> = setOf(PetAction.IDLE),
    val createdAtMillis: Long = 0L,
)
