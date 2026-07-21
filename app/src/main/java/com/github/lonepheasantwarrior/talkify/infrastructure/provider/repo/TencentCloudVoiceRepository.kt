package com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo

import android.content.Context
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.ProviderIds
import com.github.lonepheasantwarrior.talkify.domain.model.TtsProvider
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceInfo
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlEntry
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlParser

class TencentCloudVoiceRepository(
    private val context: Context
) : VoiceRepository {

    companion object {
        const val GROUP_PREMIUM = "精品音色"
        const val GROUP_LLM = "大模型音色"
        const val GROUP_NATURAL = "超自然大模型音色"
    }

    private val voices: List<VoiceXmlEntry> by lazy {
        VoiceXmlParser.parse(context, R.xml.tencent_tts_voices)
    }

    override suspend fun getVoicesForProvider(provider: TtsProvider): List<VoiceInfo> {
        if (provider.id != ProviderIds.TencentCloud.providerId) return emptyList()
        return voices.map {
            VoiceInfo(
                voiceId = it.id,
                displayName = it.displayName,
                group = it.group,
                sampleRate = parseSampleRate(it.sampleRate)
            )
        }
    }

    fun getSampleRateMap(): Map<String, Int> {
        return voices.mapNotNull { entry ->
            parseSampleRate(entry.sampleRate)?.let { entry.id to it }
        }.toMap()
    }

    private fun parseSampleRate(sampleRateStr: String): Int? {
        if (sampleRateStr.isBlank()) return null
        return try {
            val rates = sampleRateStr.split("/")
                .map { it.trim().lowercase() }
                .mapNotNull { rateStr ->
                    when {
                        rateStr.contains("8k") -> 8000
                        rateStr.contains("16k") -> 16000
                        rateStr.contains("24k") -> 24000
                        else -> null
                    }
                }
            rates.maxOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
