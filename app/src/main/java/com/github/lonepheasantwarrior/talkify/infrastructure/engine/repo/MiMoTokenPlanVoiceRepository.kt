package com.github.lonepheasantwarrior.talkify.infrastructure.engine.repo

import android.content.Context
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.EngineIds
import com.github.lonepheasantwarrior.talkify.domain.model.TtsEngine
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceInfo
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository

class MiMoTokenPlanVoiceRepository(
    private val context: Context
) : VoiceRepository {

    override suspend fun getVoicesForEngine(engine: TtsEngine): List<VoiceInfo> {
        if (engine.id != EngineIds.MiMoTokenPlan.value) return emptyList()

        val voiceIds = context.resources.getStringArray(R.array.mimo_tokenplan_voices)
        val displayNames = context.resources.getStringArray(R.array.mimo_tokenplan_voices_display_names)

        if (voiceIds.size != displayNames.size) return emptyList()

        return voiceIds.zip(displayNames).map { (id, name) ->
            VoiceInfo(voiceId = id, displayName = name)
        }
    }
}
