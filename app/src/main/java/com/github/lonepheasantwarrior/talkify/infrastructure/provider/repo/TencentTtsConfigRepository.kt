package com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo

import android.content.Context
import android.content.SharedPreferences
import com.github.lonepheasantwarrior.talkify.domain.model.BaseProviderConfig
import com.github.lonepheasantwarrior.talkify.domain.model.TencentTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.repository.ProviderConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.app.repo.SharedPreferencesAppConfigRepository

/**
 * 腾讯云语音合成供应商 - 配置仓储实现
 *
 * 使用 Android SharedPreferences 持久化存储供应商配置
 * 遵循 [ProviderConfigRepository] 接口，便于后续扩展其他存储方式
 *
 * 注意：全局配置（如"选择的供应商"）由 [SharedPreferencesAppConfigRepository] 管理
 */
class TencentTtsConfigRepository(
    context: Context
) : ProviderConfigRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getConfig(providerId: String): BaseProviderConfig {
        val prefsKey = getPrefsKey(providerId)
        return TencentTtsConfig(
            appId = sharedPreferences.getString("${prefsKey}_$KEY_APP_ID", "") ?: "",
            secretId = sharedPreferences.getString("${prefsKey}_$KEY_SECRET_ID", "") ?: "",
            secretKey = sharedPreferences.getString("${prefsKey}_$KEY_SECRET_KEY", "") ?: "",
            voiceId = sharedPreferences.getString("${prefsKey}_$KEY_VOICE_ID", "") ?: ""
        )
    }

    override fun saveConfig(providerId: String, config: BaseProviderConfig) {
        val prefsKey = getPrefsKey(providerId)
        val tencentConfig = config as? TencentTtsConfig ?: return
        sharedPreferences.edit()
            .putString("${prefsKey}_$KEY_APP_ID", tencentConfig.appId)
            .putString("${prefsKey}_$KEY_SECRET_ID", tencentConfig.secretId)
            .putString("${prefsKey}_$KEY_SECRET_KEY", tencentConfig.secretKey)
            .putString("${prefsKey}_$KEY_VOICE_ID", tencentConfig.voiceId)
            .apply()
    }

    override fun hasConfig(providerId: String): Boolean {
        val prefsKey = getPrefsKey(providerId)
        return sharedPreferences.contains("${prefsKey}_$KEY_APP_ID") ||
                sharedPreferences.contains("${prefsKey}_$KEY_SECRET_ID") ||
                sharedPreferences.contains("${prefsKey}_$KEY_SECRET_KEY") ||
                sharedPreferences.contains("${prefsKey}_$KEY_VOICE_ID")
    }

    private fun getPrefsKey(providerId: String): String {
        return "engine_${providerId}"
    }

    companion object {
        private const val PREFS_NAME = "talkify_engine_configs"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_SECRET_ID = "secret_id"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_VOICE_ID = "voice_id"
    }
}
