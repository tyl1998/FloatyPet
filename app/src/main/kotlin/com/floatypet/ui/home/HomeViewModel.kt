package com.floatypet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floatypet.core.model.PetAction
import com.floatypet.data.PetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 首页状态：组合「当前宠物」+「宠物列表」+「悬浮窗是否在运行」。
 *
 * 悬浮窗启停由 Activity 处理，ViewModel 只持有展示态。
 * [petSwitched] 通知 Activity 切换/删除宠物后重载悬浮窗。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val petRepository: PetRepository,
) : ViewModel() {

    private val overlayRunning = MutableStateFlow(false)

    private val _petSwitched = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val petSwitched: SharedFlow<Unit> = _petSwitched.asSharedFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(petRepository.currentPet, petRepository.pets, overlayRunning) { current, allPets, running ->
            if (current == null && allPets.isEmpty()) {
                HomeUiState.Empty
            } else {
                val activePet = current ?: allPets.last()
                val thumb = withContext(Dispatchers.IO) {
                    petRepository.framesOf(activePet.id, PetAction.IDLE).firstOrNull()
                }
                val summaries = withContext(Dispatchers.IO) {
                    allPets.map { pet ->
                        PetSummary(
                            id = pet.id,
                            name = pet.name,
                            thumbnail = petRepository.framesOf(pet.id, PetAction.IDLE).firstOrNull(),
                        )
                    }
                }
                HomeUiState.Ready(
                    petName = activePet.name,
                    statusText = "悠闲 · 正在晒太阳",
                    thumbnail = thumb,
                    overlayRunning = running,
                    allPets = summaries,
                    currentPetId = activePet.id,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun setOverlayRunning(running: Boolean) {
        overlayRunning.value = running
    }

    fun switchPet(petId: String) {
        viewModelScope.launch {
            petRepository.switchPet(petId)
            _petSwitched.emit(Unit)
        }
    }

    fun deletePet(petId: String) {
        viewModelScope.launch {
            petRepository.deletePet(petId)
            _petSwitched.emit(Unit)
        }
    }
}

