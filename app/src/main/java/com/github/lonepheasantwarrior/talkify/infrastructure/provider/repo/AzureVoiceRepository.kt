package com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo

import android.content.Context
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.ProviderIds
import com.github.lonepheasantwarrior.talkify.domain.model.TtsProvider
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceInfo
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlEntry
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlParser

class AzureVoiceRepository(
    private val context: Context
) : VoiceRepository {

    private val voices: List<VoiceXmlEntry> by lazy {
        VoiceXmlParser.parse(context, R.xml.microsoft_tts_voices)
    }

    override suspend fun getVoicesForProvider(provider: TtsProvider): List<VoiceInfo> {
        if (provider.id != ProviderIds.Azure.providerId) return emptyList()
        return voices.map { VoiceInfo(voiceId = it.id, displayName = it.displayName) }
    }
}
