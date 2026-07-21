package com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo

import android.content.Context
import android.content.SharedPreferences
import com.github.lonepheasantwarrior.talkify.domain.model.BaseProviderConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MicrosoftTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.repository.ProviderConfigRepository

/**
 * 微软语音合成供应商 - 配置仓储实现
 *
 * 使用 Android SharedPreferences 持久化存储供应商配置
 * 遵循 [ProviderConfigRepository] 接口，便于后续扩展其他存储方式
 *
 * 注意：微软语音合成无需 API Key，仅存储音色 ID
 */
class AzureConfigRepository(
    context: Context
) : ProviderConfigRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getConfig(providerId: String): BaseProviderConfig {
        val prefsKey = getPrefsKey(providerId)
        return MicrosoftTtsConfig(
            voiceId = sharedPreferences.getString("${prefsKey}_$KEY_VOICE_ID", "") ?: "",
            apiUrl = sharedPreferences.getString("${prefsKey}_$KEY_API_URL", "") ?: ""
        )
    }

    override fun saveConfig(providerId: String, config: BaseProviderConfig) {
        val prefsKey = getPrefsKey(providerId)
        val msConfig = config as? MicrosoftTtsConfig ?: return
        sharedPreferences.edit()
            .putString("${prefsKey}_$KEY_VOICE_ID", msConfig.voiceId)
            .putString("${prefsKey}_$KEY_API_URL", msConfig.apiUrl)
            .apply()
    }

    override fun hasConfig(providerId: String): Boolean {
        val prefsKey = getPrefsKey(providerId)
        return sharedPreferences.contains("${prefsKey}_$KEY_VOICE_ID") ||
                sharedPreferences.contains("${prefsKey}_$KEY_API_URL")
    }

    private fun getPrefsKey(providerId: String): String {
        return "engine_${providerId}"
    }

    companion object {
        private const val PREFS_NAME = "talkify_engine_configs"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_API_URL = "api_url"
    }
}
