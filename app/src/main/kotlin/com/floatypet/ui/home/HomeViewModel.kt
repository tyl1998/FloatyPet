package com.floatypet.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 首页状态持有者。骨架：先返回一个占位 Ready 状态，
 * 后续接 PetRepository（DataStore + 素材库）后改为响应式 Flow。
 *
 * 悬浮窗的实际启停由 Activity 处理（需要 Context + 权限），ViewModel 只持有
 * 「是否在桌面」的展示状态，由 Activity 通过 [setOverlayRunning] 回写。
 */
@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(
        HomeUiState.Ready(
            petName = "小橘",
            statusText = "悠闲 · 正在晒太阳",
            overlayRunning = false,
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 由 Activity 在真正启停悬浮窗后回写展示状态。 */
    fun setOverlayRunning(running: Boolean) {
        val current = _uiState.value
        if (current is HomeUiState.Ready) {
            _uiState.value = current.copy(overlayRunning = running)
        }
    }
}
