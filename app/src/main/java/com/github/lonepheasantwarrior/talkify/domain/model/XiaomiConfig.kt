package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 小米 MiMo 语音合成供应商配置
 *
 * 继承 [BaseProviderConfig]，封装小米 MiMo 供应商所需的配置信息
 * 使用小米服务的 API Key 进行认证
 *
 * @property voiceId 声音 ID，如 "mimo_default"
 * @property apiUrl 自定义 API 地址，为空时使用默认地址
 *                   （默认：https://api.xiaomimimo.com/v1/chat/completions）
 * @property modelId 自定义模型 ID，为空时使用默认模型（默认：mimo-v2-tts）
 * @property apiKey 小米平台的 API Key，用于认证
 *                  从小米开放平台获取
 */
data class XiaomiConfig(
    override val voiceId: String = "",
    override val apiUrl: String = "",
    override val modelId: String = "",
    val apiKey: String = ""
) : BaseProviderConfig(voiceId, apiUrl, modelId)
