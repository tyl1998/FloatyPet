package com.floatypet.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floatypet.ai.AiConfigStore
import com.floatypet.ai.ImageGenClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val configStore: AiConfigStore,
    private val genClient: ImageGenClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiConfigUiState())
    val uiState: StateFlow<AiConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = configStore.config.first()
            _uiState.value = AiConfigUiState(
                apiKey = cfg.apiKey,
                baseUrl = cfg.baseUrl,
                genModel = cfg.genModel,
                isConfigured = cfg.isConfigured,
            )
        }
    }

    fun onApiKeyChange(v: String) { _uiState.value = _uiState.value.copy(apiKey = v, testResult = null) }
    fun onBaseUrlChange(v: String) { _uiState.value = _uiState.value.copy(baseUrl = v, testResult = null) }
    fun onModelChange(v: String) { _uiState.value = _uiState.value.copy(genModel = v, testResult = null) }

    fun testAndSave() {
        val s = _uiState.value
        if (s.apiKey.isBlank() || s.baseUrl.isBlank()) {
            _uiState.value = s.copy(testResult = "API Key 和 Base URL 不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testing = true, testResult = null)
            val result = genClient.testConnection(s.baseUrl, s.apiKey)
            result.fold(
                onSuccess = { msg ->
                    configStore.save(s.apiKey, s.baseUrl, s.genModel)
                    _uiState.value = _uiState.value.copy(
                        testing = false,
                        testResult = "✅ $msg，已保存",
                        isConfigured = true,
                        saved = true,
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        testing = false,
                        testResult = "❌ ${err.message}",
                    )
                },
            )
        }
    }
}
