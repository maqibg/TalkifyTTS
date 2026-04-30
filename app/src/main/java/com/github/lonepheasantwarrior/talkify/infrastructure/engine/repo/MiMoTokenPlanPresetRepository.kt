package com.github.lonepheasantwarrior.talkify.infrastructure.engine.repo

import android.content.Context
import android.content.SharedPreferences

data class VoicePreset(
    val name: String,
    val type: String, // "voicedesign" or "voiceclone"
    val description: String = "", // for voicedesign
    val audioPath: String = "" // for voiceclone
)

class MiMoTokenPlanPresetRepository(
    context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPresets(): List<VoicePreset> {
        val presets = mutableListOf<VoicePreset>()
        val presetNames = sharedPreferences.getStringSet(KEY_PRESET_NAMES, emptySet()) ?: emptySet()
        for (name in presetNames) {
            val type = sharedPreferences.getString("${KEY_PREFIX}${name}_type", "") ?: ""
            val desc = sharedPreferences.getString("${KEY_PREFIX}${name}_desc", "") ?: ""
            val audioPath = sharedPreferences.getString("${KEY_PREFIX}${name}_audio", "") ?: ""
            if (type.isNotBlank()) {
                presets.add(VoicePreset(name, type, desc, audioPath))
            }
        }
        return presets.sortedBy { it.name }
    }

    fun savePreset(preset: VoicePreset) {
        val names = sharedPreferences.getStringSet(KEY_PRESET_NAMES, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        names.add(preset.name)
        sharedPreferences.edit()
            .putStringSet(KEY_PRESET_NAMES, names)
            .putString("${KEY_PREFIX}${preset.name}_type", preset.type)
            .putString("${KEY_PREFIX}${preset.name}_desc", preset.description)
            .putString("${KEY_PREFIX}${preset.name}_audio", preset.audioPath)
            .apply()
    }

    fun deletePreset(name: String) {
        val names = sharedPreferences.getStringSet(KEY_PRESET_NAMES, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        names.remove(name)
        sharedPreferences.edit()
            .putStringSet(KEY_PRESET_NAMES, names)
            .remove("${KEY_PREFIX}${name}_type")
            .remove("${KEY_PREFIX}${name}_desc")
            .remove("${KEY_PREFIX}${name}_audio")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "talkify_mimo_presets"
        private const val KEY_PRESET_NAMES = "preset_names"
        private const val KEY_PREFIX = "preset_"
    }
}
