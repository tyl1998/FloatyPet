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
import com.floatypet.asset.importer.FrameImporter
import com.floatypet.asset.importer.GifFrameExtractor
import com.floatypet.asset.importer.VideoFrameExtractor
import com.floatypet.asset.store.PetStore
import com.floatypet.core.model.Pet
import com.floatypet.core.model.PetAction
import com.floatypet.data.PetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val videoExtractor: VideoFrameExtractor,
    private val frameImporter: FrameImporter,
    private val gifExtractor: GifFrameExtractor,
    private val petStore: PetStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    // 原始正方形图（抠图/恢复的基础）
    private var squareOriginal: Bitmap? = null

    // 描述词防抖写盘 Job
    private var descSaveJob: Job? = null

    // 当前编辑会话的宠物 ID：新宠用时间戳生成，续编辑时从 currentPet 取
    private var activePetId: String = System.currentTimeMillis().toString()

    init {
        viewModelScope.launch {
            val cfg = aiConfigStore.config.first()
            _uiState.value = _uiState.value.copy(
                isAiConfigured = cfg.isConfigured,
                hasVisionModel = cfg.hasVisionModel,
            )
        }
    }

    // ── 手动导入路径 ──────────────────────────────────────────

    /** 从已有宠物恢复编辑（不需要重新选图）。直接展示 AI 面板，并还原上次的描述词。 */
    fun resume() {
        viewModelScope.launch {
            _uiState.value = EditUiState(loading = true)
            val cfg = aiConfigStore.config.first()
            val currentId = withContext(Dispatchers.IO) {
                petRepository.currentPet.first()?.id
            }
            if (currentId != null) activePetId = currentId
            val (savedDesc, idleFrame) = withContext(Dispatchers.IO) {
                val pet = petStore.readPet(activePetId)
                val desc = pet?.description.orEmpty()
                // Restore the IDLE frame so "照片分析" can re-analyze without re-uploading
                val frame = petStore.loadFrames(activePetId, PetAction.IDLE).firstOrNull()
                Pair(desc, frame)
            }
            if (idleFrame != null) squareOriginal = idleFrame
            val states = initActionStates()
            _uiState.value = EditUiState(
                loading = false,
                isAiConfigured = cfg.isConfigured,
                hasVisionModel = cfg.hasVisionModel,
                aiPanelOpen = true,
                petDescription = savedDesc,
                descSource = if (savedDesc.isNotBlank()) DescSource.SAVED else DescSource.NONE,
                actionStates = states,
                aiGenerationComplete = states.values.any { it.status == ActionStatus.DONE },
                isResumed = true,
                petThumbnail = idleFrame,
            )
        }
    }

    fun load(uri: Uri) {
        // 新宠会话：每次 load 生成新的 petId，旧宠帧不受影响
        activePetId = System.currentTimeMillis().toString()
        viewModelScope.launch {
            _uiState.value = EditUiState(loading = true)
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
                _uiState.value = EditUiState(
                    loading = false,
                    error = "图片读取失败",
                    isAiConfigured = cfg.isConfigured,
                    hasVisionModel = cfg.hasVisionModel,
                )
            } else {
                squareOriginal = square
                // Auto-apply background removal so the overlay always gets a transparent sprite.
                // User sees the result immediately; can fix with eraser or press "还原" if needed.
                val cutout = withContext(Dispatchers.IO) {
                    ImageOps.removeFlatBackground(square, tolerance = 36)
                }
                _uiState.value = EditUiState(
                    loading = false,
                    working = cutout,
                    cutoutApplied = true,
                    isAiConfigured = cfg.isConfigured,
                    hasVisionModel = cfg.hasVisionModel,
                    actionStates = initActionStates(),
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
                .onSuccess { pet ->
                    activePetId = pet.id
                    val states = initActionStates()
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        aiPanelOpen = true,
                        actionStates = states,
                        aiGenerationComplete = states.values.any { it.status == ActionStatus.DONE },
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(saving = false, error = "保存失败：${it.message}") }
        }
    }

    fun saveAndFinish(name: String) {
        val bmp = _uiState.value.working ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            runCatching { petRepository.createPetFromImage(name.ifBlank { "宝贝" }, bmp) }
                .onSuccess { pet ->
                    activePetId = pet.id
                    _uiState.value = _uiState.value.copy(saving = false, saved = true)
                }
                .onFailure { _uiState.value = _uiState.value.copy(saving = false, error = "保存失败：${it.message}") }
        }
    }

    // ── AI 生成面板 ───────────────────────────────────────────

    fun openAiPanel() {
        _uiState.value = _uiState.value.copy(aiPanelOpen = true)
    }

    fun onDescriptionChange(v: String) {
        _uiState.value = _uiState.value.copy(
            petDescription = v,
            descSource = if (v.isBlank()) DescSource.NONE else DescSource.MANUAL,
        )
        // Debounce: persist to disk 1 s after typing stops
        descSaveJob?.cancel()
        if (v.isNotBlank()) {
            descSaveJob = viewModelScope.launch {
                delay(1000)
                saveDescriptionToDisk(v)
            }
        }
    }

    /** 视觉分析照片，自动填充描述词。 */
    fun analyzePhoto() {
        val bitmap = squareOriginal ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(analyzing = true, aiError = null)
            val result = runCatching { generationEngine.analyzePhoto(bitmap) }
            result.fold(
                onSuccess = { desc ->
                    _uiState.value = _uiState.value.copy(
                        analyzing = false,
                        petDescription = desc,
                        descSource = DescSource.VISION,
                    )
                    // Persist immediately — re-entry without generation would lose the result otherwise
                    saveDescriptionToDisk(desc)
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        analyzing = false,
                        aiError = "照片分析失败：${err.message}",
                    )
                },
            )
        }
    }

    fun toggleActionSelection(action: PetAction) {
        val current = _uiState.value.selectedActions
        _uiState.value = _uiState.value.copy(
            selectedActions = if (current.contains(action)) current - action else current + action,
        )
    }

    fun selectAllActions() {
        _uiState.value = _uiState.value.copy(selectedActions = PetAction.entries.toSet())
    }

    fun selectCoreActions() {
        _uiState.value = _uiState.value.copy(selectedActions = PetAction.CORE.toSet())
    }

    /** 批量生成选中动作。 */
    fun startGeneration() {
        val desc = _uiState.value.petDescription.trim()
        if (desc.isBlank()) {
            _uiState.value = _uiState.value.copy(aiError = "请先填写宠物描述（或使用照片分析自动填写）")
            return
        }
        val actions = _uiState.value.selectedActions.toList()
        if (actions.isEmpty()) {
            _uiState.value = _uiState.value.copy(aiError = "请至少选择一个要生成的动作")
            return
        }
        val petId = activePetId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                aiError = null,
                aiGenerationComplete = false,
                actionStates = _uiState.value.actionStates + actions.associate {
                    it to ActionState(ActionStatus.PENDING)
                },
            )
            // 描述词持久化，以便下次续生成无需重填
            withContext(Dispatchers.IO) {
                val pet = petStore.readPet(petId)
                if (pet != null) petStore.savePet(pet.copy(description = desc))
            }
            generationEngine.generate(desc, petId, actions).collect { progress ->
                when {
                    progress.isDone -> {
                        petRepository.registerCurrentPet(petId)
                        _uiState.value = _uiState.value.copy(aiGenerationComplete = true)
                    }
                    progress.bitmap == null && progress.error == null -> {
                        _uiState.value = _uiState.value.copy(
                            actionStates = _uiState.value.actionStates +
                                (progress.action to ActionState(ActionStatus.GENERATING)),
                        )
                    }
                    else -> {
                        val newStatus = if (progress.error != null) ActionStatus.FAILED else ActionStatus.DONE
                        _uiState.value = _uiState.value.copy(
                            actionStates = _uiState.value.actionStates +
                                (progress.action to ActionState(newStatus, progress.bitmap)),
                            previewBitmap = progress.bitmap ?: _uiState.value.previewBitmap,
                            aiError = if (progress.error != null) "部分动作生成失败" else _uiState.value.aiError,
                        )
                    }
                }
            }
        }
    }

    /** 单独重新生成某个动作。 */
    fun regenerateAction(action: PetAction) {
        val desc = _uiState.value.petDescription.trim()
        if (desc.isBlank()) {
            _uiState.value = _uiState.value.copy(aiError = "请先填写宠物描述")
            return
        }
        val petId = activePetId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates +
                    (action to ActionState(ActionStatus.GENERATING)),
                aiError = null,
            )
            val result = runCatching { generationEngine.generateAction(petId, action, desc) }
            val newState = result.fold(
                onSuccess = { bmp -> ActionState(ActionStatus.DONE, bmp) },
                onFailure = { err -> ActionState(ActionStatus.FAILED, error = err.message) },
            )
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates + (action to newState),
                previewBitmap = newState.thumbnail ?: _uiState.value.previewBitmap,
            )
        }
    }

    // ── 视频导入 ──────────────────────────────────────────────

    fun requestVideoImport(action: PetAction) {
        _uiState.value = _uiState.value.copy(videoPickerForAction = action)
    }

    fun clearVideoPickerRequest() {
        _uiState.value = _uiState.value.copy(videoPickerForAction = null)
    }

    fun requestImageImport(action: PetAction) {
        _uiState.value = _uiState.value.copy(imagePickerForAction = action)
    }

    fun clearImagePickerRequest() {
        _uiState.value = _uiState.value.copy(imagePickerForAction = null)
    }

    fun requestGifImport(action: PetAction) {
        _uiState.value = _uiState.value.copy(gifPickerForAction = action)
    }

    fun clearGifPickerRequest() {
        _uiState.value = _uiState.value.copy(gifPickerForAction = null)
    }

    fun importGifFrames(uri: Uri, action: PetAction) {
        val petId = activePetId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates +
                    (action to ActionState(ActionStatus.GENERATING)),
                gifPickerForAction = null,
            )
            val result = runCatching {
                val extracted = gifExtractor.extract(petId, action, uri)
                if (extracted.savedCount == 0) error("GIF 解析失败，请确认文件是否为有效的 GIF 动图")
                updatePetActions(petId, action)
                extracted.firstFrame
            }
            val newState = result.fold(
                onSuccess = { thumb -> ActionState(ActionStatus.DONE, thumb) },
                onFailure = { err -> ActionState(ActionStatus.FAILED, error = err.message) },
            )
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates + (action to newState),
                previewBitmap = newState.thumbnail ?: _uiState.value.previewBitmap,
                aiGenerationComplete = _uiState.value.aiGenerationComplete ||
                    (action == PetAction.IDLE && newState.status == ActionStatus.DONE),
            )
        }
    }

    /** 用户选完图片序列后调用，去背景后存为该动作的帧序列。 */
    fun importImageFrames(uris: List<Uri>, action: PetAction) {
        if (uris.isEmpty()) return
        val petId = activePetId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates +
                    (action to ActionState(ActionStatus.GENERATING)),
                imagePickerForAction = null,
            )
            val result = runCatching {
                val imported = frameImporter.importImages(petId, action, uris)
                if (imported.savedCount == 0) error("图片导入失败，请重试")
                updatePetActions(petId, action)
                imported.firstFrame
            }
            val newState = result.fold(
                onSuccess = { thumb -> ActionState(ActionStatus.DONE, thumb) },
                onFailure = { err -> ActionState(ActionStatus.FAILED, error = err.message) },
            )
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates + (action to newState),
                previewBitmap = newState.thumbnail ?: _uiState.value.previewBitmap,
                aiGenerationComplete = _uiState.value.aiGenerationComplete ||
                    (action == PetAction.IDLE && newState.status == ActionStatus.DONE),
            )
        }
    }

    /** 用户选完视频后调用，抽帧并存储为该动作的帧序列。 */
    fun importVideoFrames(uri: Uri, action: PetAction) {
        val petId = activePetId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates +
                    (action to ActionState(ActionStatus.GENERATING)),
                videoPickerForAction = null,
            )
            val result = runCatching {
                val frames = videoExtractor.extract(uri, frameCount = 8, removeBackground = true)
                if (frames.isEmpty()) error("视频无法解析，请换一个视频文件")
                frames.forEachIndexed { idx, bmp ->
                    petStore.writeFrame(petId, action, frameIndex = idx + 1, bitmap = bmp)
                }
                updatePetActions(petId, action)
                frames.first()
            }
            val newState = result.fold(
                onSuccess = { thumb -> ActionState(ActionStatus.DONE, thumb) },
                onFailure = { err -> ActionState(ActionStatus.FAILED, error = err.message) },
            )
            _uiState.value = _uiState.value.copy(
                actionStates = _uiState.value.actionStates + (action to newState),
                previewBitmap = newState.thumbnail ?: _uiState.value.previewBitmap,
            )
        }
    }

    /** 用户点击"完成·放到桌面"后触发，设 saved=true 导航回首页。 */
    fun finishAiGeneration() {
        _uiState.value = _uiState.value.copy(saved = true)
    }

    // ── 私有工具 ──────────────────────────────────────────────

    private suspend fun initActionStates(): Map<PetAction, ActionState> =
        withContext(Dispatchers.IO) {
            PetAction.entries.associateWith { action ->
                val frames = petStore.loadFrames(activePetId, action)
                if (frames.isNotEmpty()) ActionState(ActionStatus.DONE, frames.first())
                else ActionState(ActionStatus.PENDING)
            }
        }

    /** 将描述词写入 pet.json，仅在 pet 记录已存在时才写（新宠未保存前无 pet 记录）。 */
    private suspend fun saveDescriptionToDisk(desc: String) {
        withContext(Dispatchers.IO) {
            val pet = petStore.readPet(activePetId) ?: return@withContext
            if (pet.description != desc) petStore.savePet(pet.copy(description = desc))
        }
    }

    /** 为 petId 的 pet.json 追加一个已完成的动作，pet 不存在时自动创建。注册为当前宠物。 */
    private suspend fun updatePetActions(petId: String, action: PetAction) {
        withContext(Dispatchers.IO) {
            val existing = petStore.readPet(petId)
            val updated = existing?.copy(availableActions = existing.availableActions + action)
                ?: Pet(id = petId, name = "宝贝", availableActions = setOf(action),
                    createdAtMillis = System.currentTimeMillis())
            petStore.savePet(updated)
        }
        petRepository.registerCurrentPet(petId)
    }
}
