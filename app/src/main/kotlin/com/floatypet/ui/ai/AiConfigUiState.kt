package com.floatypet.ui.ai

data class AiConfigUiState(
    val apiKey: String = "",
    val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
    val genModel: String = "doubao-seedream-4-0-250828",
    val isConfigured: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
)
