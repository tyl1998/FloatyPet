package com.floatypet.ui.adopt

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatypet.core.model.PetAction
import com.floatypet.ui.theme.Ink
import com.floatypet.ui.theme.Ink2
import com.floatypet.ui.theme.Mint
import com.floatypet.ui.theme.Primary

@Composable
fun EditRoute(
    imageUri: Uri?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onGoAiConfig: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(imageUri) {
        if (imageUri != null) viewModel.load(imageUri) else viewModel.resume()
    }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }
    // AI 生成完成后用户手动点"完成"按钮才导航，onAiFinish 负责注册宠物并跳回首页
    val onAiFinish: () -> Unit = { viewModel.finishAiGeneration() }

    // 视频选择器
    val videoLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val action = uiState.videoPickerForAction ?: return@rememberLauncherForActivityResult
        viewModel.clearVideoPickerRequest()
        if (uri != null) viewModel.importVideoFrames(uri, action)
    }
    LaunchedEffect(uiState.videoPickerForAction) {
        if (uiState.videoPickerForAction != null) {
            videoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
        }
    }

    // 多图选择器（最多 9 张，作为帧序列）
    val imageLauncher = rememberLauncherForActivityResult(PickMultipleVisualMedia(9)) { uris ->
        val action = uiState.imagePickerForAction ?: return@rememberLauncherForActivityResult
        viewModel.clearImagePickerRequest()
        if (uris.isNotEmpty()) viewModel.importImageFrames(uris, action)
    }
    LaunchedEffect(uiState.imagePickerForAction) {
        if (uiState.imagePickerForAction != null) {
            imageLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }

    // GIF 选择器（单文件，image/gif MIME）
    val gifLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        val action = uiState.gifPickerForAction ?: return@rememberLauncherForActivityResult
        viewModel.clearGifPickerRequest()
        if (uri != null) viewModel.importGifFrames(uri, action)
    }
    LaunchedEffect(uiState.gifPickerForAction) {
        if (uiState.gifPickerForAction != null) {
            gifLauncher.launch("image/gif")
        }
    }

    EditScreen(
        uiState = uiState,
        onToggleCutout = viewModel::toggleCutout,
        onToggleEraser = { viewModel.setEraser(!uiState.eraserMode) },
        onErase = viewModel::eraseAt,
        onSave = { viewModel.save("宝贝") },
        onSaveAndFinish = { viewModel.saveAndFinish("宝贝") },
        onBack = onBack,
        onDescriptionChange = viewModel::onDescriptionChange,
        onAnalyzePhoto = viewModel::analyzePhoto,
        onToggleActionSelection = viewModel::toggleActionSelection,
        onSelectAll = viewModel::selectAllActions,
        onSelectCore = viewModel::selectCoreActions,
        onStartGeneration = viewModel::startGeneration,
        onRegenerateAction = viewModel::regenerateAction,
        onImportVideoForAction = viewModel::requestVideoImport,
        onImportImagesForAction = viewModel::requestImageImport,
        onImportGifForAction = viewModel::requestGifImport,
        onGoAiConfig = onGoAiConfig,
        onAiFinish = onAiFinish,
    )
}

@Composable
internal fun EditScreen(
    uiState: EditUiState,
    onToggleCutout: () -> Unit,
    onToggleEraser: () -> Unit,
    onErase: (px: Int, py: Int, radius: Int) -> Unit,
    onSave: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onBack: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAnalyzePhoto: () -> Unit,
    onToggleActionSelection: (PetAction) -> Unit,
    onSelectAll: () -> Unit,
    onSelectCore: () -> Unit,
    onStartGeneration: () -> Unit,
    onRegenerateAction: (PetAction) -> Unit,
    onImportVideoForAction: (PetAction) -> Unit,
    onImportImagesForAction: (PetAction) -> Unit,
    onImportGifForAction: (PetAction) -> Unit,
    onGoAiConfig: () -> Unit,
    onAiFinish: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("编辑桌宠", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // 图片预览（续编辑模式下跳过，直接看动作面板）
        if (!uiState.isResumed) PreviewArea(uiState, onErase)

        if (!uiState.aiPanelOpen) {
            ManualEditPanel(uiState, onToggleCutout, onToggleEraser, onSave, onSaveAndFinish, onBack, onGoAiConfig)
        } else {
            AiGenerationPanel(
                uiState = uiState,
                onDescriptionChange = onDescriptionChange,
                onAnalyzePhoto = onAnalyzePhoto,
                onToggleActionSelection = onToggleActionSelection,
                onSelectAll = onSelectAll,
                onSelectCore = onSelectCore,
                onStartGeneration = onStartGeneration,
                onRegenerateAction = onRegenerateAction,
                onImportVideoForAction = onImportVideoForAction,
                onImportImagesForAction = onImportImagesForAction,
                onImportGifForAction = onImportGifForAction,
                onFinish = onAiFinish,
            )
        }

        uiState.aiError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PreviewArea(uiState: EditUiState, onErase: (Int, Int, Int) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        when {
            uiState.loading -> CircularProgressIndicator(color = Primary)
            uiState.previewBitmap != null -> Image(
                bitmap = uiState.previewBitmap.asImageBitmap(),
                contentDescription = "AI 生成预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
            )
            uiState.working != null -> PreviewCanvas(uiState, onErase)
            uiState.error != null -> Text(uiState.error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ManualEditPanel(
    uiState: EditUiState,
    onToggleCutout: () -> Unit,
    onToggleEraser: () -> Unit,
    onSave: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onBack: () -> Unit,
    onGoAiConfig: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (uiState.eraserMode) {
            Text("橡皮擦模式：在图上拖动擦除多余部分",
                style = MaterialTheme.typography.bodyMedium, color = Ink2)
        }
        FilledTonalButton(onClick = onToggleCutout, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.cutoutApplied) "已去背景 · 点击还原" else "一键去背景（纯色底）")
        }
        FilledTonalButton(onClick = onToggleEraser, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.eraserMode) "退出橡皮擦" else "橡皮擦微调")
        }
        // Primary: save idle frame and open AI action panel
        Button(
            onClick = onSave,
            enabled = uiState.working != null && !uiState.saving,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.saving) "保存中…" else "完成编辑，配置动作 ✨")
        }
        // Secondary: skip AI generation, go straight to desktop
        TextButton(
            onClick = onSaveAndFinish,
            enabled = uiState.working != null && !uiState.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("直接放到桌面（不生成动作）", color = Ink2, fontSize = 13.sp)
        }

        // AI 入口（当 AI 未配置时引导配置）
        if (!uiState.isAiConfigured) {
            FilledTonalButton(onClick = onGoAiConfig, modifier = Modifier.fillMaxWidth()) {
                Text("配置 AI 服务 → 生成专属动作")
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("重新选图") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiGenerationPanel(
    uiState: EditUiState,
    onDescriptionChange: (String) -> Unit,
    onAnalyzePhoto: () -> Unit,
    onToggleActionSelection: (PetAction) -> Unit,
    onSelectAll: () -> Unit,
    onSelectCore: () -> Unit,
    onStartGeneration: () -> Unit,
    onRegenerateAction: (PetAction) -> Unit,
    onImportVideoForAction: (PetAction) -> Unit,
    onImportImagesForAction: (PetAction) -> Unit,
    onImportGifForAction: (PetAction) -> Unit,
    onFinish: () -> Unit = {},
) {
    val isGenerating = uiState.actionStates.values.any { it.status == ActionStatus.GENERATING }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 续编辑模式：顶部显示宠物原始图，让用户清楚在编辑哪只
        if (uiState.petThumbnail != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    bitmap = uiState.petThumbnail.asImageBitmap(),
                    contentDescription = "宠物原始图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEEEEEE)),
                )
                Text("AI 生成专属形象", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("AI 生成专属形象", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // 描述词区域
        DescriptionSection(uiState, onDescriptionChange, onAnalyzePhoto)

        // 动作选择
        Text("选择要生成的动作", style = MaterialTheme.typography.labelLarge, color = Ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSelectAll) { Text("全选", fontSize = 12.sp) }
            TextButton(onClick = onSelectCore) { Text("仅核心 3 个", fontSize = 12.sp) }
        }

        // 9个动作网格
        ActionGrid(
            uiState = uiState,
            onToggleSelection = onToggleActionSelection,
            onRegenerate = onRegenerateAction,
            onImportVideo = onImportVideoForAction,
            onImportImages = onImportImagesForAction,
            onImportGif = onImportGifForAction,
            isGlobalGenerating = isGenerating,
        )

        // 导入提示
        Text(
            "导入 GIF/视频：推荐纯色背景（白/绿），或使用本透明贴纸 GIF；复杂背景去除效果有限。\n效果最佳：用 AI 生成专属帧，或导入带透明度的 GIF。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink2.copy(alpha = 0.65f),
            lineHeight = 16.sp,
        )

        // 生成按钮
        val doneCount = uiState.actionStates.values.count { it.status == ActionStatus.DONE }
        if (!isGenerating || doneCount > 0) {
            Button(
                onClick = onStartGeneration,
                enabled = !isGenerating && uiState.selectedActions.isNotEmpty() && uiState.petDescription.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    when {
                        isGenerating -> "生成中… ($doneCount/${uiState.selectedActions.size})"
                        doneCount > 0 -> "重新生成选中动作"
                        else -> "开始生成 (${uiState.selectedActions.size} 个动作)"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            // 全程生成中，显示总进度
            val total = uiState.selectedActions.size
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (total > 0) doneCount.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                )
                Text("已完成 $doneCount / $total 个动作",
                    style = MaterialTheme.typography.bodySmall, color = Ink2)
            }
        }

        // 生成完成 → 显示"放到桌面"按钮（Bug 1 fix：不再自动 nav，用户手动触发）
        if (uiState.aiGenerationComplete && !isGenerating) {
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("完成 · 放到桌面 🐾", fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else if (doneCount > 0 && !uiState.aiGenerationComplete) {
            Text("IDLE 已完成，生成其他动作中…",
                style = MaterialTheme.typography.bodySmall, color = Mint)
        }
    }
}

@Composable
private fun DescriptionSection(
    uiState: EditUiState,
    onDescriptionChange: (String) -> Unit,
    onAnalyzePhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("宠物描述", style = MaterialTheme.typography.labelLarge,
                color = Ink, modifier = Modifier.weight(1f))
            if (uiState.hasVisionModel) {
                FilledTonalButton(
                    onClick = onAnalyzePhoto,
                    enabled = !uiState.analyzing,
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                ) {
                    if (uiState.analyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp, color = Primary)
                        Spacer(Modifier.width(4.dp))
                        Text("分析中…", fontSize = 12.sp)
                    } else {
                        Text("✨ 照片分析", fontSize = 12.sp)
                    }
                }
            }
        }
        OutlinedTextField(
            value = uiState.petDescription,
            onValueChange = onDescriptionChange,
            placeholder = { Text("例：橘色短毛猫，圆脸，黄色眼睛，胖乎乎，卡通2D风格") },
            enabled = !uiState.analyzing,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            supportingText = {
                when (uiState.descSource) {
                    DescSource.VISION -> Text("来自照片分析 ✓", color = Mint, fontSize = 11.sp)
                    DescSource.SAVED  -> Text("已保存的描述 · 可重新分析", color = Ink2, fontSize = 11.sp)
                    DescSource.MANUAL -> Text("手动输入", color = Ink2, fontSize = 11.sp)
                    DescSource.NONE   -> Text("描述越详细，生成越贴合你的宠物外观", color = Ink2, fontSize = 11.sp)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionGrid(
    uiState: EditUiState,
    onToggleSelection: (PetAction) -> Unit,
    onRegenerate: (PetAction) -> Unit,
    onImportVideo: (PetAction) -> Unit,
    onImportImages: (PetAction) -> Unit,
    onImportGif: (PetAction) -> Unit,
    isGlobalGenerating: Boolean,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        PetAction.entries.forEach { action ->
            val state = uiState.actionStates[action] ?: ActionState()
            val selected = uiState.selectedActions.contains(action)
            ActionTile(
                action = action,
                state = state,
                selected = selected,
                onToggleSelect = { onToggleSelection(action) },
                onRegenerate = { onRegenerate(action) },
                onImportVideo = { onImportVideo(action) },
                onImportImages = { onImportImages(action) },
                onImportGif = { onImportGif(action) },
                enabled = !isGlobalGenerating,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionTile(
    action: PetAction,
    state: ActionState,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onRegenerate: () -> Unit,
    onImportVideo: () -> Unit,
    onImportImages: () -> Unit,
    onImportGif: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isGenerating = state.status == ActionStatus.GENERATING
    val isDone = state.status == ActionStatus.DONE

    Surface(
        onClick = onToggleSelect,
        enabled = enabled && !isGenerating,
        shape = RoundedCornerShape(12.dp),
        color = when {
            isGenerating -> Color(0xFFFFF1EA)
            selected -> Color(0xFFFFE0CC)
            else -> Color(0xFFF5F5F5)
        },
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Primary) else null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 缩略图 or 状态指示
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isGenerating -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Primary)
                    isDone && state.thumbnail != null -> Image(
                        bitmap = state.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    isDone -> Icon(Icons.Default.Check, null,
                        tint = Mint, modifier = Modifier.size(24.dp))
                    else -> Text(actionEmoji(action), fontSize = 22.sp)
                }
            }
            Text(actionLabel(action), style = MaterialTheme.typography.labelSmall,
                color = if (selected) Primary else Ink2, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)

            // 操作按钮行
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (isDone && !isGenerating) {
                    IconButton(onClick = onRegenerate, enabled = enabled,
                        modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Refresh, "重新生成", tint = Ink2,
                            modifier = Modifier.size(14.dp))
                    }
                }
                if (!isGenerating) {
                    TextButton(
                        onClick = onImportImages, enabled = enabled,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.height(22.dp),
                    ) { Text("图片", fontSize = 9.sp, color = Primary) }
                    TextButton(
                        onClick = onImportGif, enabled = enabled,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.height(22.dp),
                    ) { Text("GIF", fontSize = 9.sp, color = Mint) }
                    TextButton(
                        onClick = onImportVideo, enabled = enabled,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.height(22.dp),
                    ) { Text("视频", fontSize = 9.sp, color = Ink2) }
                }
            }
        }
    }
}

@Composable
private fun PreviewCanvas(uiState: EditUiState, onErase: (Int, Int, Int) -> Unit) {
    val bmp = uiState.working ?: return
    var boxPx by remember { mutableStateOf(1) }
    val density = LocalDensity.current
    val eraseRadiusPx = with(density) { 14.dp.toPx() }
    val squareSizePx = with(density) { 16.dp.toPx() }
    val checker1 = Color(0xFFCCCCCC)
    val checker2 = Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            // Checkerboard pattern: transparent pixels show as grey/white grid
            .drawBehind {
                val cols = (size.width / squareSizePx).toInt() + 1
                val rows = (size.height / squareSizePx).toInt() + 1
                for (row in 0..rows) {
                    for (col in 0..cols) {
                        drawRect(
                            color = if ((row + col) % 2 == 0) checker1 else checker2,
                            topLeft = Offset(col * squareSizePx, row * squareSizePx),
                            size = androidx.compose.ui.geometry.Size(squareSizePx, squareSizePx),
                        )
                    }
                }
            }
            .onSizeChanged { boxPx = it.width.coerceAtLeast(1) }
            .then(if (uiState.eraserMode) {
                Modifier.pointerInput(bmp, boxPx) {
                    detectDragGestures { change, _ ->
                        val scale = bmp.width.toFloat() / boxPx
                        onErase(
                            (change.position.x * scale).toInt(),
                            (change.position.y * scale).toInt(),
                            (eraseRadiusPx * scale).toInt().coerceAtLeast(4),
                        )
                    }
                }
            } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("UNUSED_EXPRESSION") uiState.eraseTick
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "宠物预览",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

private fun actionLabel(action: PetAction): String = when (action) {
    PetAction.IDLE    -> "待机"
    PetAction.SLEEP   -> "睡觉"
    PetAction.GREET   -> "打招呼"
    PetAction.SIT     -> "坐下"
    PetAction.STRETCH -> "伸懒腰"
    PetAction.SHRINK  -> "缩缩"
    PetAction.HAPPY   -> "开心"
    PetAction.SAD     -> "委屈"
    PetAction.WALK    -> "走路"
}

private fun actionEmoji(action: PetAction): String = when (action) {
    PetAction.IDLE    -> "😊"
    PetAction.SLEEP   -> "😴"
    PetAction.GREET   -> "👋"
    PetAction.SIT     -> "🧘"
    PetAction.STRETCH -> "🙆"
    PetAction.SHRINK  -> "😨"
    PetAction.HAPPY   -> "🥳"
    PetAction.SAD     -> "😢"
    PetAction.WALK    -> "🚶"
}
