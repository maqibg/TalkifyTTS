package com.github.lonepheasantwarrior.talkify.domain.model

data class MiMoTokenPlanConfig(
    override val voiceId: String = "",
    val apiKey: String = "",
    val model: String = "mimo-v2.5-tts",
    val voiceDesignDescription: String = "",
    val voiceCloneAudioPath: String = "",
    val styleTag: String = "",
    val dialect: String = "",
    val userMessage: String = "",
    val preGenerateCount: Int = 0
) : BaseEngineConfig(voiceId)
