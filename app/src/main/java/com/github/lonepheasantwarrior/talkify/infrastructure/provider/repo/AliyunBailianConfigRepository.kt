package com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo

import android.content.Context
import android.content.SharedPreferences
import com.github.lonepheasantwarrior.talkify.domain.model.BaseProviderConfig
import com.github.lonepheasantwarrior.talkify.domain.model.AliyunBailianConfig
import com.github.lonepheasantwarrior.talkify.domain.repository.ProviderConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.app.repo.SharedPreferencesAppConfigRepository

/**
 * 通义千问3语音合成供应商 - 配置仓储实现
 *
 * 使用 Android SharedPreferences 持久化存储供应商配置
 * 遵循 [ProviderConfigRepository] 接口，便于后续扩展其他存储方式
 *
 * 注意：全局配置（如"选择的供应商"）由 [SharedPreferencesAppConfigRepository] 管理
 */
class AliyunBailianConfigRepository(
    context: Context
) : ProviderConfigRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getConfig(providerId: String): BaseProviderConfig {
        val prefsKey = getPrefsKey(providerId)
        return AliyunBailianConfig(
            apiKey = sharedPreferences.getString("${prefsKey}_$KEY_API_KEY", "") ?: "",
            voiceId = sharedPreferences.getString("${prefsKey}_$KEY_VOICE_ID", "") ?: "",
            apiUrl = sharedPreferences.getString("${prefsKey}_$KEY_API_URL", "") ?: "",
            modelId = sharedPreferences.getString("${prefsKey}_$KEY_MODEL_ID", "") ?: ""
        )
    }

    override fun saveConfig(providerId: String, config: BaseProviderConfig) {
        val prefsKey = getPrefsKey(providerId)
        val qwenConfig = config as? AliyunBailianConfig ?: return
        sharedPreferences.edit()
            .putString("${prefsKey}_$KEY_API_KEY", qwenConfig.apiKey)
            .putString("${prefsKey}_$KEY_VOICE_ID", qwenConfig.voiceId)
            .putString("${prefsKey}_$KEY_API_URL", qwenConfig.apiUrl)
            .putString("${prefsKey}_$KEY_MODEL_ID", qwenConfig.modelId)
            .apply()
    }

    override fun hasConfig(providerId: String): Boolean {
        val prefsKey = getPrefsKey(providerId)
        return sharedPreferences.contains("${prefsKey}_$KEY_API_KEY") ||
                sharedPreferences.contains("${prefsKey}_$KEY_VOICE_ID") ||
                sharedPreferences.contains("${prefsKey}_$KEY_API_URL") ||
                sharedPreferences.contains("${prefsKey}_$KEY_MODEL_ID")
    }

    private fun getPrefsKey(providerId: String): String {
        return "engine_${providerId}"
    }

    companion object {
        private const val PREFS_NAME = "talkify_engine_configs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_API_URL = "api_url"
        private const val KEY_MODEL_ID = "model_id"
    }
}
