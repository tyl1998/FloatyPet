package com.floatypet.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore by preferencesDataStore(name = "ai_config")

/**
 * AI 服务配置持久化。API Key 加密存储，BaseURL/Model 明文（不敏感）。
 * 用户配置的服务商信息仅用于调用用户指定的端点，不做其他用途（CLAUDE.md §8）。
 */
@Singleton
class AiConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystore: KeystoreHelper,
) {

    data class AiConfig(
        val apiKey: String = "",
        val baseUrl: String = DEFAULT_BASE_URL,
        val genModel: String = DEFAULT_MODEL,
    ) {
        val isConfigured: Boolean
            get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && genModel.isNotBlank()
    }

    val config: Flow<AiConfig> = context.aiDataStore.data.map { prefs ->
        val enc = prefs[KEY_ENC_API_KEY].orEmpty()
        val apiKey = if (enc.isNotBlank()) keystore.decrypt(enc).orEmpty() else ""
        AiConfig(
            apiKey = apiKey,
            baseUrl = prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            genModel = prefs[KEY_GEN_MODEL] ?: DEFAULT_MODEL,
        )
    }

    suspend fun save(apiKey: String, baseUrl: String, genModel: String) {
        val enc = if (apiKey.isNotBlank()) keystore.encrypt(apiKey) else ""
        context.aiDataStore.edit { p ->
            p[KEY_ENC_API_KEY] = enc
            p[KEY_BASE_URL] = baseUrl.trimEnd('/')
            p[KEY_GEN_MODEL] = genModel
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        const val DEFAULT_MODEL = "doubao-seedream-4-0-250828"
        private val KEY_ENC_API_KEY = stringPreferencesKey("enc_api_key")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_GEN_MODEL = stringPreferencesKey("gen_model")
    }
}
