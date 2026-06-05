package com.floatypet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floatypet.core.model.PetAction
import com.floatypet.data.PetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 首页状态：组合「当前宠物（来自 PetRepository）」+「悬浮窗是否在运行」。
 *
 * 悬浮窗启停由 Activity 处理（需 Context + 权限），ViewModel 只持有展示态，
 * 由 Activity 通过 [setOverlayRunning] 回写。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val petRepository: PetRepository,
) : ViewModel() {

    private val overlayRunning = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> =
        combine(petRepository.currentPet, overlayRunning) { pet, running ->
            if (pet == null) {
                HomeUiState.Empty
            } else {
                val thumb = withContext(Dispatchers.IO) {
                    petRepository.framesOf(pet.id, PetAction.IDLE).firstOrNull()
                }
                HomeUiState.Ready(
                    petName = pet.name,
                    statusText = "悠闲 · 正在晒太阳",
                    thumbnail = thumb,
                    overlayRunning = running,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    /** 由 Activity 在真正启停悬浮窗后回写展示状态。 */
    fun setOverlayRunning(running: Boolean) {
        overlayRunning.value = running
    }
}
