package com.github.lonepheasantwarrior.talkify.infrastructure.engine.repo

import android.content.Context
import android.content.SharedPreferences
import com.github.lonepheasantwarrior.talkify.domain.model.BaseEngineConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MiMoTokenPlanConfig
import com.github.lonepheasantwarrior.talkify.domain.repository.EngineConfigRepository

class MiMoTokenPlanConfigRepository(
    context: Context
) : EngineConfigRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getConfig(engineId: String): BaseEngineConfig {
        val prefsKey = getPrefsKey(engineId)
        return MiMoTokenPlanConfig(
            apiKey = sharedPreferences.getString("${prefsKey}_$KEY_API_KEY", "") ?: "",
            voiceId = sharedPreferences.getString("${prefsKey}_$KEY_VOICE_ID", "") ?: "",
            model = sharedPreferences.getString("${prefsKey}_$KEY_MODEL", "mimo-v2.5-tts") ?: "mimo-v2.5-tts",
            voiceDesignDescription = sharedPreferences.getString("${prefsKey}_$KEY_VOICE_DESIGN_DESC", "") ?: "",
            voiceCloneAudioPath = sharedPreferences.getString("${prefsKey}_$KEY_VOICE_CLONE_AUDIO", "") ?: "",
            styleTag = sharedPreferences.getString("${prefsKey}_$KEY_STYLE_TAG", "") ?: "",
            dialect = sharedPreferences.getString("${prefsKey}_$KEY_DIALECT", "") ?: "",
            userMessage = sharedPreferences.getString("${prefsKey}_$KEY_USER_MESSAGE", "") ?: "",
            preGenerateCount = sharedPreferences.getInt("${prefsKey}_$KEY_PRE_GENERATE_COUNT", 0)
        )
    }

    override fun saveConfig(engineId: String, config: BaseEngineConfig) {
        val prefsKey = getPrefsKey(engineId)
        val mimoConfig = config as? MiMoTokenPlanConfig ?: return
        sharedPreferences.edit()
            .putString("${prefsKey}_$KEY_API_KEY", mimoConfig.apiKey)
            .putString("${prefsKey}_$KEY_VOICE_ID", mimoConfig.voiceId)
            .putString("${prefsKey}_$KEY_MODEL", mimoConfig.model)
            .putString("${prefsKey}_$KEY_VOICE_DESIGN_DESC", mimoConfig.voiceDesignDescription)
            .putString("${prefsKey}_$KEY_VOICE_CLONE_AUDIO", mimoConfig.voiceCloneAudioPath)
            .putString("${prefsKey}_$KEY_STYLE_TAG", mimoConfig.styleTag)
            .putString("${prefsKey}_$KEY_DIALECT", mimoConfig.dialect)
            .putString("${prefsKey}_$KEY_USER_MESSAGE", mimoConfig.userMessage)
            .putInt("${prefsKey}_$KEY_PRE_GENERATE_COUNT", mimoConfig.preGenerateCount)
            .apply()
    }

    override fun hasConfig(engineId: String): Boolean {
        val prefsKey = getPrefsKey(engineId)
        return sharedPreferences.contains("${prefsKey}_$KEY_API_KEY")
    }

    private fun getPrefsKey(engineId: String): String {
        return "engine_${engineId}"
    }

    companion object {
        private const val PREFS_NAME = "talkify_engine_configs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_MODEL = "model"
        private const val KEY_VOICE_DESIGN_DESC = "voice_design_desc"
        private const val KEY_VOICE_CLONE_AUDIO = "voice_clone_audio"
        private const val KEY_STYLE_TAG = "style_tag"
        private const val KEY_DIALECT = "dialect"
        private const val KEY_USER_MESSAGE = "user_message"
        private const val KEY_PRE_GENERATE_COUNT = "pre_generate_count"
    }
}
