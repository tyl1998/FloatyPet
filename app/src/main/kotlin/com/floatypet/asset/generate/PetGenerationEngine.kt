package com.floatypet.asset.generate

import android.graphics.Bitmap
import com.floatypet.ai.ImageGenClient
import com.floatypet.asset.edit.ImageOps
import com.floatypet.asset.store.PetStore
import com.floatypet.core.model.Pet
import com.floatypet.core.model.PetAction
import com.floatypet.core.model.PetAssetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 逐帧生成引擎（策略 A：每个动作 1 张姿势图，运动引擎提供生命感）。
 *
 * 流程：描述词 → 逐动作调 ImageGenClient → 去白底 → 归一化 → 存帧。
 * IDLE 优先生成，生成完即可投放桌面；其余动作后台依次补全。
 * 单帧失败最多重试 1 次，不影响其他帧（AGENT.md §1.2）。
 */
@Singleton
class PetGenerationEngine @Inject constructor(
    private val client: ImageGenClient,
    private val store: PetStore,
) {
    // 风格后缀：控制生成图的底色与样式（白底便于后续去背景）
    private val styleSuffix =
        "，2D 卡通贴纸风格，全身，纯白色背景，无文字，无阴影，干净轮廓，高质量细节"

    // 每个动作的姿势描述（中文，为国内模型优化）
    private val actionDesc: Map<PetAction, String> = mapOf(
        PetAction.IDLE to "正常坐姿或站姿，放松，微微抬头",
        PetAction.SLEEP to "蜷缩趴下，闭眼安睡，身体缩成一团",
        PetAction.GREET to "抬起头，前爪微微抬起，热情迎接，眼神明亮",
        PetAction.SIT to "端正坐姿，尾巴绕在身体旁边，专注眼神",
        PetAction.STRETCH to "伸懒腰，前爪向前尽力伸展，后腿蹬直，打哈欠",
        PetAction.SHRINK to "身体微微缩起，警觉或受惊，耳朵贴平",
        PetAction.HAPPY to "非常开心，眼睛弯成月牙，尾巴高高翘起",
        PetAction.SAD to "耷拉着头，无精打采，委屈的表情",
        PetAction.WALK to "迈步行走的侧视姿势，充满活力",
    )

    data class GenerationProgress(
        val action: PetAction,
        val bitmap: Bitmap? = null,      // 非 null = 该动作已完成
        val error: String? = null,
        val completedCount: Int = 0,
        val totalCount: Int = PetAction.entries.size,
        val isDone: Boolean = false,
    )

    /**
     * 生成全部 9 个动作帧，以 Flow<GenerationProgress> 实时上报进度。
     *
     * IDLE 优先（第一个），其余并不保证顺序。
     * 调用方在 completedCount == 1（IDLE done）时就可以把宠物放到桌面。
     *
     * @param description 用户对宠物的文字描述（例："橘色短毛猫，圆脸，黄色眼睛"）
     * @param petId 目标宠物 id，帧写入 PetStore 对应目录
     */
    fun generate(description: String, petId: String): Flow<GenerationProgress> = flow {
        // IDLE 优先，然后其余按表顺序
        val order = listOf(PetAction.IDLE) + (PetAction.entries - PetAction.IDLE)
        val available = mutableSetOf<PetAction>()
        var completed = 0

        for (action in order) {
            val desc = actionDesc[action] ?: continue
            val prompt = "${description}，${desc}${styleSuffix}"

            val bitmap = generateWithRetry(prompt)
            completed++

            if (bitmap != null) {
                val processed = postProcess(bitmap)
                store.writeFrame(petId, action, frameIndex = 1, bitmap = processed)
                available += action
                emit(GenerationProgress(action, processed, null, completed))
            } else {
                emit(GenerationProgress(action, null, "生成失败，已跳过", completed))
            }
        }

        // 保存 pet.json（更新 availableActions）
        val pet = store.readPet(petId)?.copy(availableActions = available)
            ?: Pet(
                id = petId,
                name = "宝贝",
                assetType = PetAssetType.SPRITE_2D,
                availableActions = available,
            )
        store.savePet(pet)
        emit(GenerationProgress(PetAction.IDLE, null, null, completed, isDone = true))
    }

    // ----

    private suspend fun generateWithRetry(prompt: String, maxRetry: Int = 1): Bitmap? {
        repeat(maxRetry + 1) { attempt ->
            runCatching { client.generateImageBitmap(prompt) }
                .onSuccess { return it }
                .onFailure { if (attempt == maxRetry) return null }
        }
        return null
    }

    /** 去白底 + 归一化到 512×512 透明 PNG。 */
    private fun postProcess(src: Bitmap): Bitmap {
        val cutout = ImageOps.removeFlatBackground(src, tolerance = 40)
        return ImageOps.normalizeSprite(cutout, 512)
    }
}
