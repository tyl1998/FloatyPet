package com.floatypet.ui.adopt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floatypet.ai.AiConfigStore
import com.floatypet.asset.edit.ImageOps
import com.floatypet.asset.generate.PetGenerationEngine
import com.floatypet.data.PetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val petRepository: PetRepository,
    private val aiConfigStore: AiConfigStore,
    private val generationEngine: PetGenerationEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var squareOriginal: Bitmap? = null

    init {
        viewModelScope.launch {
            val cfg = aiConfigStore.config.first()
            _uiState.value = _uiState.value.copy(isAiConfigured = cfg.isConfigured)
        }
    }

    // ── 手动导入路径 ──────────────────────────────────────────

    fun load(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = EditUiState(loading = true)
            // 重新检查 AI 配置
            val cfg = aiConfigStore.config.first()
            val square = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri).use { input ->
                        val raw = BitmapFactory.decodeStream(input) ?: return@runCatching null
                        ImageOps.centerSquare(ImageOps.downscale(raw, 1024))
                    }
                }.getOrNull()
            }
            if (square == null) {
                _uiState.value = EditUiState(loading = false, error = "图片读取失败", isAiConfigured = cfg.isConfigured)
            } else {
                squareOriginal = square
                _uiState.value = EditUiState(
                    loading = false,
                    working = square,
                    isAiConfigured = cfg.isConfigured,
                )
            }
        }
    }

    fun toggleCutout() {
        val base = squareOriginal ?: return
        viewModelScope.launch {
            val applied = !_uiState.value.cutoutApplied
            val result = if (applied) {
                withContext(Dispatchers.IO) { ImageOps.removeFlatBackground(base, tolerance = 36) }
            } else {
                base.copy(Bitmap.Config.ARGB_8888, true)
            }
            _uiState.value = _uiState.value.copy(working = result, cutoutApplied = applied)
        }
    }

    fun setEraser(on: Boolean) { _uiState.value = _uiState.value.copy(eraserMode = on) }

    fun eraseAt(px: Int, py: Int, radius: Int) {
        val bmp = _uiState.value.working ?: return
        ImageOps.erase(bmp, px, py, radius)
        _uiState.value = _uiState.value.copy(working = bmp, eraseTick = _uiState.value.eraseTick + 1)
    }

    fun save(name: String) {
        val bmp = _uiState.value.working ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            runCatching { petRepository.createPetFromImage(name.ifBlank { "宝贝" }, bmp) }
                .onSuccess { _uiState.value = _uiState.value.copy(saving = false, saved = true) }
                .onFailure { _uiState.value = _uiState.value.copy(saving = false, error = "保存失败：${it.message}") }
        }
    }

    // ── AI 生成路径 ───────────────────────────────────────────

    fun openAiPanel() { _uiState.value = _uiState.value.copy(aiPanelOpen = true) }

    fun onAiDescriptionChange(v: String) { _uiState.value = _uiState.value.copy(aiDescription = v) }

    /**
     * 启动 AI 生成：根据描述词生成全部 9 个动作帧。
     * IDLE 生成完后 [uiState.aiCompletedCount] == 1，调用方可立即放到桌面预览。
     * 全部完成后 [uiState.aiDone] == true，自动保存并触发导航回首页。
     */
    fun startAiGeneration() {
        val description = _uiState.value.aiDescription.trim()
        if (description.isBlank()) {
            _uiState.value = _uiState.value.copy(aiError = "请先输入宠物描述（如：橘色短毛猫，圆脸，黄色眼睛）")
            return
        }
        val petId = "pet_main"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                aiGenerating = true,
                aiCompletedCount = 0,
                aiError = null,
                aiDone = false,
            )
            generationEngine.generate(description, petId).collect { progress ->
                if (progress.isDone) {
                    _uiState.value = _uiState.value.copy(
                        aiGenerating = false,
                        aiDone = true,
                        saved = true,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        aiCurrentAction = progress.action,
                        aiCompletedCount = progress.completedCount,
                        aiLatestBitmap = progress.bitmap ?: _uiState.value.aiLatestBitmap,
                        aiError = progress.error,
                    )
                }
            }
        }
    }
}
