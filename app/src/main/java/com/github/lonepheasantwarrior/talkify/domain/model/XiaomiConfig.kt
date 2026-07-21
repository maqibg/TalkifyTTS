package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 小米 MiMo 语音合成供应商配置
 *
 * 继承 [BaseProviderConfig]，封装小米 MiMo 供应商所需的配置信息
 * 使用小米服务的 API Key 进行认证
 *
 * @property voiceId 声音 ID，如 "mimo_default"、"冰糖"、"Mia" 等
 *                   （完整列表见 res/xml/xiaomi_mimo_voices_v2p5.xml）
 * @property apiUrl 自定义 API 地址，为空时使用默认地址
 *                   （默认：https://api.xiaomimimo.com/v1/chat/completions）
 * @property modelId 自定义模型 ID，为空时使用默认模型（默认：mimo-v2.5-tts）
 * @property apiKey 小米平台的 API Key，用于认证
 *                  从小米开放平台获取
 * @property styleInstruction 可选的风格指令（自然语言描述朗读风格、语气等）。
 *                             对应 v2.5 API 的 user role 消息。
 *                             例如："用温柔的语气朗读"、"用新闻播报风格朗读"。
 *                             为空时不发送 user 消息。
 */
data class XiaomiConfig(
    override val voiceId: String = "",
    override val apiUrl: String = "",
    override val modelId: String = "",
    val apiKey: String = "",
    val styleInstruction: String = ""
) : BaseProviderConfig(voiceId, apiUrl, modelId)
