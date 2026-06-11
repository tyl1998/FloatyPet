package com.floatypet.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AiConfigRoute(
    onSaved: () -> Unit,
    viewModel: AiConfigViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onSaved() }
    AiConfigScreen(
        state = s,
        onApiKeyChange = viewModel::onApiKeyChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onModelChange = viewModel::onModelChange,
        onVisionModelChange = viewModel::onVisionModelChange,
        onTestAndSave = viewModel::testAndSave,
    )
}

@Composable
internal fun AiConfigScreen(
    state: AiConfigUiState,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onVisionModelChange: (String) -> Unit,
    onTestAndSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("AI 服务配置", style = MaterialTheme.typography.titleLarge)
        Text(
            "配置后可生成你的宠物专属形象。API Key 加密存储在本设备，不会上传到任何服务器。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("ARK_API_KEY 或你的密钥") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            placeholder = { Text("https://ark.cn-beijing.volces.com/api/v3") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.genModel,
            onValueChange = onModelChange,
            label = { Text("图像生成模型") },
            placeholder = { Text("doubao-seedream-4-0-250828") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.visionModel,
            onValueChange = onVisionModelChange,
            label = { Text("视觉分析模型（可选）") },
            placeholder = { Text("doubao-1.5-vision-pro-32k") },
            supportingText = { Text("填写后，生成前自动分析你的宠物照片，保证各动作风格一致") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onTestAndSave,
            enabled = !state.testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.testing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(if (state.testing) "验证中…" else "验证并保存")
        }

        state.testResult?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
