package com.floatypet.ui.adopt

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatypet.core.model.PetAction

@Composable
fun EditRoute(
    imageUri: Uri,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onGoAiConfig: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(imageUri) { viewModel.load(imageUri) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    EditScreen(
        uiState = uiState,
        onToggleCutout = viewModel::toggleCutout,
        onToggleEraser = { viewModel.setEraser(!uiState.eraserMode) },
        onErase = viewModel::eraseAt,
        onSave = { viewModel.save("宝贝") },
        onBack = onBack,
        onOpenAiPanel = viewModel::openAiPanel,
        onAiDescriptionChange = viewModel::onAiDescriptionChange,
        onStartAiGeneration = viewModel::startAiGeneration,
        onGoAiConfig = onGoAiConfig,
    )
}

@Composable
internal fun EditScreen(
    uiState: EditUiState,
    onToggleCutout: () -> Unit,
    onToggleEraser: () -> Unit,
    onErase: (px: Int, py: Int, radius: Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onOpenAiPanel: () -> Unit,
    onAiDescriptionChange: (String) -> Unit,
    onStartAiGeneration: () -> Unit,
    onGoAiConfig: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("编辑你的桌宠", style = MaterialTheme.typography.titleLarge)

        // 图片预览
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.loading -> CircularProgressIndicator()
                uiState.aiLatestBitmap != null -> Image(
                    bitmap = uiState.aiLatestBitmap.asImageBitmap(),
                    contentDescription = "AI 生成预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.working != null -> PreviewCanvas(uiState, onErase)
                uiState.error != null -> Text(uiState.error, color = MaterialTheme.colorScheme.error)
            }
        }

        // ── AI 生成面板 ────────────────────────────────────────
        if (uiState.aiPanelOpen) {
            AiGenerationPanel(uiState, onAiDescriptionChange, onStartAiGeneration)
        } else {
            // 手动编辑按钮组
            if (!uiState.aiGenerating) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.eraserMode) {
                        Text(
                            "橡皮擦模式：在图上拖动擦除多余部分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = onToggleCutout, modifier = Modifier.fillMaxWidth()) {
                        Text(if (uiState.cutoutApplied) "已去背景 · 点击还原" else "一键去背景（纯色底）")
                    }
                    FilledTonalButton(onClick = onToggleEraser, modifier = Modifier.fillMaxWidth()) {
                        Text(if (uiState.eraserMode) "退出橡皮擦" else "橡皮擦微调")
                    }
                    Button(
                        onClick = onSave,
                        enabled = uiState.working != null && !uiState.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.saving) "保存中…" else "手动编辑完成，放到桌面")
                    }

                    // AI 生成入口
                    if (uiState.isAiConfigured) {
                        Button(
                            onClick = onOpenAiPanel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("✨ AI 生成专属形象 + 所有动作")
                        }
                    } else {
                        FilledTonalButton(onClick = onGoAiConfig, modifier = Modifier.fillMaxWidth()) {
                            Text("配置 AI 服务 → 一键生成全套动作")
                        }
                    }

                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("重新选图")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiGenerationPanel(
    uiState: EditUiState,
    onDescriptionChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI 生成专属形象", style = MaterialTheme.typography.titleMedium)
        Text(
            "描述你的宠物外观，AI 会生成对应风格的 2D 角色 + 全套动作（坐、睡、打招呼…）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.aiDescription,
            onValueChange = onDescriptionChange,
            label = { Text("宠物描述") },
            placeholder = { Text("例：橘色短毛猫，圆脸，黄色眼睛，胖乎乎") },
            enabled = !uiState.aiGenerating,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        if (!uiState.aiGenerating && !uiState.aiDone) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.aiDescription.isNotBlank(),
            ) { Text("开始生成全部动作") }
        }

        // 进度
        if (uiState.aiGenerating || uiState.aiDone) {
            val progress = uiState.aiCompletedCount.toFloat() / uiState.aiTotalCount
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (uiState.aiDone) "全部生成完成 🎉"
                    else "正在生成：${actionLabel(uiState.aiCurrentAction)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${uiState.aiCompletedCount}/${uiState.aiTotalCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.aiCompletedCount >= 1 && !uiState.aiDone) {
                Text(
                    "待机动作已就绪，其他动作后台继续生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.aiError?.let {
            Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun actionLabel(action: PetAction?): String = when (action) {
    PetAction.IDLE -> "待机"
    PetAction.SLEEP -> "睡觉"
    PetAction.GREET -> "打招呼"
    PetAction.SIT -> "坐下"
    PetAction.STRETCH -> "伸懒腰"
    PetAction.SHRINK -> "缩缩"
    PetAction.HAPPY -> "开心"
    PetAction.SAD -> "委屈"
    PetAction.WALK -> "走路"
    null -> "…"
}

@Composable
private fun PreviewCanvas(
    uiState: EditUiState,
    onErase: (px: Int, py: Int, radius: Int) -> Unit,
) {
    val bmp = uiState.working ?: return
    var boxPx by remember { mutableStateOf(1) }
    val density = LocalDensity.current
    val eraseRadiusPx = with(density) { 14.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { boxPx = it.width.coerceAtLeast(1) }
            .then(
                if (uiState.eraserMode) {
                    Modifier.pointerInput(bmp, boxPx) {
                        detectDragGestures { change, _ ->
                            val scale = bmp.width.toFloat() / boxPx
                            val px = (change.position.x * scale).toInt()
                            val py = (change.position.y * scale).toInt()
                            val r = (eraseRadiusPx * scale).toInt().coerceAtLeast(4)
                            onErase(px, py, r)
                        }
                    }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("UNUSED_EXPRESSION") uiState.eraseTick
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "宠物预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
