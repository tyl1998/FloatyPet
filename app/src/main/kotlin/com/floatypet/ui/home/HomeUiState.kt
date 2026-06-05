package com.floatypet.ui.home

import android.graphics.Bitmap

/** 首页 UI 状态。不可变 data class，通过 copy() 更新（CLAUDE.md §5.2）。 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** 还没有任何宠物——引导用户领养。 */
    data object Empty : HomeUiState

    /** 已有宠物。 */
    data class Ready(
        val petName: String,
        val statusText: String,
        val thumbnail: Bitmap?,
        val overlayRunning: Boolean,
    ) : HomeUiState
}
