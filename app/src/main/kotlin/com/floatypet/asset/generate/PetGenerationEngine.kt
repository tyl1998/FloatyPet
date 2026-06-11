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
 * AI 逐帧生成引擎。
 *
 * 支持：
 *  - 单动作生成：[generateAction]
 *  - 批量生成（可指定动作集合）：[generate]
 *  - 可选视觉分析：调用 [ImageGenClient.analyzeImage] 从宠物照片提取一致的角色描述
 *
 * 生成流程：
 *   1. (可选) vision 模型分析照片 → 提取角色外观描述
 *   2. 角色描述 + 动作姿势描述 → 文生图
 *   3. 去白底 + 归一化 → 存帧
 */
@Singleton
class PetGenerationEngine @Inject constructor(
    private val client: ImageGenClient,
    private val store: PetStore,
) {
    // 风格后缀：纯白底便于抠图，2D 卡通风格
    private val styleSuffix =
        "，2D 卡通贴纸风格，全身，纯白色背景，无文字，无阴影，干净轮廓，高质量细节，前后动作风格统一"

    /**
     * 每个动作的多帧姿势描述。帧数越多动画越流畅，但 API 调用越多。
     * IDLE/WALK 4帧（关键动作），SLEEP 3帧，其余 2帧。
     */
    private val frameDescs: Map<PetAction, List<String>> = mapOf(
        PetAction.IDLE to listOf(
            "放松站姿，自然呼气，胸部微收，正面",
            "放松站姿，自然吸气，胸部微抬，正面",
            "放松站姿，头微微向左侧倾，耳朵竖起",
            "放松站姿，打个小哈欠，嘴微张，眼睛眯起",
        ),
        PetAction.WALK to listOf(
            "行走，左前腿向前迈步，右后腿蹬地，侧视",
            "行走，四肢自然过渡，双脚接近，侧视",
            "行走，右前腿向前迈步，左后腿蹬地，侧视",
            "行走，四肢收拢，身体微微起伏，侧视",
        ),
        PetAction.SLEEP to listOf(
            "蜷缩趴下，闭眼安睡，自然呼气，胸部微收",
            "蜷缩趴下，闭眼安睡，轻轻吸气，胸部微微起伏",
            "蜷缩趴下，侧卧放松，闭眼，尾巴轻轻摆动",
        ),
        PetAction.GREET to listOf(
            "抬起前爪打招呼，热情迎接，眼神明亮，面朝正面",
            "前爪举得更高，热情挥动，笑脸盈盈",
        ),
        PetAction.SIT to listOf(
            "端正坐姿，尾巴绕在身体旁边，专注眼神",
            "坐姿，微微转头，尾巴轻轻摆动",
        ),
        PetAction.STRETCH to listOf(
            "伸懒腰，前爪向前尽力伸展，后腿蹬直，打哈欠",
            "伸懒腰结束，身体回正，满足感神情",
        ),
        PetAction.SHRINK to listOf(
            "身体微微缩起，警觉受惊，耳朵贴平，眼睛圆睁",
            "身体缩得更紧，毛发微微炸开，瞳孔放大",
        ),
        PetAction.HAPPY to listOf(
            "非常开心，眼睛弯成月牙，尾巴高高翘起，蹦跳起始",
            "蹦跳到最高点，四爪腾空，嘴角上扬",
        ),
        PetAction.SAD to listOf(
            "耷拉着头，无精打采，委屈的表情，尾巴低垂",
            "低着头，眼角微湿，身体微微颤抖",
        ),
    )

    // 单动作描述（generateAction 复用第一帧描述）
    private val actionDesc: Map<PetAction, String> = frameDescs.mapValues { (_, v) -> v.first() }

    /** 生成进度事件。 */
    data class GenerationProgress(
        val action: PetAction,
        val bitmap: Bitmap? = null,
        val error: String? = null,
        val completedCount: Int = 0,
        val totalCount: Int = 0,
        val isDone: Boolean = false,
    )

    /**
     * 批量生成指定动作集合，以 [Flow] 实时上报进度。
     *
     * @param description  角色描述（用户手动填写，或视觉分析结果）
     * @param petId        目标宠物 id
     * @param actions      要生成的动作列表（默认全部 9 个，IDLE 始终排第一）
     * @param refBitmap    宠物原始照片（非 null 且已配置视觉模型时，先做视觉分析）
     */
    fun generate(
        description: String,
        petId: String,
        actions: List<PetAction> = listOf(PetAction.IDLE) + (PetAction.entries - PetAction.IDLE),
        refBitmap: Bitmap? = null,
    ): Flow<GenerationProgress> = flow {
        // IDLE 必须排第一（IDLE 完成即可上桌面预览）
        val order = if (actions.contains(PetAction.IDLE)) {
            listOf(PetAction.IDLE) + actions.filter { it != PetAction.IDLE }
        } else actions

        val total = order.size
        var completed = 0

        for (action in order) {
            val frames = frameDescs[action] ?: listOf(actionDesc[action] ?: continue)
            emit(GenerationProgress(action, null, null, completed, total))

            var firstBitmap: Bitmap? = null
            var anySuccess = false
            for ((idx, frameDesc) in frames.withIndex()) {
                val prompt = "${description}，${frameDesc}${styleSuffix}"
                val bitmap = generateWithRetry(prompt)
                if (bitmap != null) {
                    val processed = postProcess(bitmap)
                    store.writeFrame(petId, action, frameIndex = idx + 1, bitmap = processed)
                    if (firstBitmap == null) firstBitmap = processed
                    anySuccess = true
                }
            }
            completed++

            if (anySuccess) {
                emit(GenerationProgress(action, firstBitmap, null, completed, total))
            } else {
                emit(GenerationProgress(action, null, "生成失败，已跳过", completed, total))
            }
        }

        // 更新 pet.json 的 availableActions
        updateAvailableActions(petId)
        emit(GenerationProgress(PetAction.IDLE, null, null, completed, total, isDone = true))
    }

    /**
     * 单独重新生成一个动作。挂起函数，成功返回生成的 Bitmap（已归一化），失败返回 null。
     */
    suspend fun generateAction(
        petId: String,
        action: PetAction,
        description: String,
    ): Bitmap? {
        val desc = actionDesc[action] ?: return null
        val prompt = "${description}，${desc}${styleSuffix}"
        val bitmap = generateWithRetry(prompt) ?: return null
        val processed = postProcess(bitmap)
        store.writeFrame(petId, action, frameIndex = 1, bitmap = processed)
        updateAvailableActions(petId)
        return processed
    }

    /**
     * 用视觉模型分析宠物照片，返回统一的角色描述字符串。
     * 需要已配置 visionModel（[AiConfigStore.AiConfig.hasVisionModel]）。
     */
    suspend fun analyzePhoto(bitmap: Bitmap): String =
        client.analyzeImage(bitmap)

    // ----

    private suspend fun generateWithRetry(prompt: String, maxRetry: Int = 1): Bitmap? {
        repeat(maxRetry + 1) { attempt ->
            runCatching { client.generateImageBitmap(prompt) }
                .onSuccess { return it }
                .onFailure { if (attempt == maxRetry) return null }
        }
        return null
    }

    private fun postProcess(src: Bitmap): Bitmap {
        val cutout = ImageOps.removeFlatBackground(src, tolerance = 40)
        return ImageOps.normalizeSprite(cutout, 512)
    }

    private fun updateAvailableActions(petId: String) {
        val existing = store.readPet(petId)
        val available = PetAction.entries.filter { action ->
            store.loadFrames(petId, action).isNotEmpty()
        }.toSet()
        val updated = existing?.copy(availableActions = available)
            ?: Pet(id = petId, name = "宝贝", assetType = PetAssetType.SPRITE_2D, availableActions = available)
        store.savePet(updated)
    }
}
