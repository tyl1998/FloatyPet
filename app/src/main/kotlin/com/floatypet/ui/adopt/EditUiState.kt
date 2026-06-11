package com.floatypet.ui.adopt

import android.graphics.Bitmap
import com.floatypet.core.model.PetAction

/** 素材编辑页 UI 状态（CLAUDE.md §5.2，不可变）。 */
data class EditUiState(
    val loading: Boolean = true,
    val working: Bitmap? = null,
    val cutoutApplied: Boolean = false,
    val eraserMode: Boolean = false,
    val eraseTick: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,

    // ── AI 配置状态 ──
    val isAiConfigured: Boolean = false,
    val hasVisionModel: Boolean = false,

    // ── AI 生成面板 ──
    val aiPanelOpen: Boolean = false,

    /** 宠物描述词（用户手动输入 或 视觉分析结果） */
    val petDescription: String = "",
    val descSource: DescSource = DescSource.NONE,
    val analyzing: Boolean = false,  // 视觉分析中

    /** 用户选择要生成的动作集合 */
    val selectedActions: Set<PetAction> = PetAction.CORE.toSet(),

    /** 各动作当前的生成状态 */
    val actionStates: Map<PetAction, ActionState> = emptyMap(),

    /** 最近生成完成的一帧预览 */
    val previewBitmap: Bitmap? = null,

    /** 非 null 时，EditScreen 触发视频选择器，完成后调用 clearVideoPick */
    val videoPickerForAction: PetAction? = null,

    /** 非 null 时，EditScreen 触发多图选择器，完成后调用 clearImagePick */
    val imagePickerForAction: PetAction? = null,

    /** 非 null 时，EditScreen 触发 GIF 文件选择器，完成后调用 clearGifPick */
    val gifPickerForAction: PetAction? = null,

    val aiError: String? = null,

    /** AI 生成至少完成 IDLE，可以放到桌面了 */
    val aiGenerationComplete: Boolean = false,

    /** true = 从已有宠物进入续编辑，无照片，直接展示 AI 面板 */
    val isResumed: Boolean = false,

    /** 续编辑时顶部展示的宠物 idle 帧缩略图 */
    val petThumbnail: Bitmap? = null,
)

data class ActionState(
    val status: ActionStatus = ActionStatus.PENDING,
    val thumbnail: Bitmap? = null,
    val error: String? = null,
)

enum class ActionStatus { PENDING, GENERATING, DONE, FAILED }

enum class DescSource {
    NONE,    // 未填写
    MANUAL,  // 用户手动填写
    VISION,  // 视觉模型分析结果
    SAVED,   // 从 pet.json 恢复的历史记录
}
