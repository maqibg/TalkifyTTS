package com.github.lonepheasantwarrior.talkify.infrastructure.engine.repo

import android.content.Context
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.EngineIds
import com.github.lonepheasantwarrior.talkify.domain.model.TtsEngine
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceInfo
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlEntry
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlParser

class Qwen3TtsVoiceRepository(
    private val context: Context
) : VoiceRepository {

    private val voices: List<VoiceXmlEntry> by lazy {
        VoiceXmlParser.parse(context, R.xml.bailian_qwen3_tts_voices)
    }

    override suspend fun getVoicesForEngine(engine: TtsEngine): List<VoiceInfo> {
        if (engine.id != EngineIds.Qwen3Tts.value) return emptyList()
        return voices.map { VoiceInfo(voiceId = it.id, displayName = it.displayName) }
    }
}
