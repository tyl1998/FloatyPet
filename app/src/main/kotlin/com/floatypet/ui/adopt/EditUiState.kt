package com.floatypet.ui.adopt

import android.graphics.Bitmap
import com.floatypet.core.model.PetAction

/** 素材编辑页 UI 状态（CLAUDE.md §5.2，不可变）。 */
data class EditUiState(
    val loading: Boolean = true,
    /** 当前预览用的工作位图（已裁成方形；抠图/橡皮擦会更新它） */
    val working: Bitmap? = null,
    val cutoutApplied: Boolean = false,
    val eraserMode: Boolean = false,
    /** 橡皮擦原地修改同一 Bitmap，递增此值强制重组 */
    val eraseTick: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,

    // ── AI 生成 ──
    val isAiConfigured: Boolean = false,
    /** 用户输入的宠物描述词 */
    val aiDescription: String = "",
    /** AI 面板是否展开 */
    val aiPanelOpen: Boolean = false,
    val aiGenerating: Boolean = false,
    val aiCompletedCount: Int = 0,
    val aiTotalCount: Int = PetAction.entries.size,
    /** 当前正在生成的动作 */
    val aiCurrentAction: PetAction? = null,
    /** 最近生成好的一帧预览（给用户看效果） */
    val aiLatestBitmap: Bitmap? = null,
    val aiDone: Boolean = false,
    val aiError: String? = null,
)
