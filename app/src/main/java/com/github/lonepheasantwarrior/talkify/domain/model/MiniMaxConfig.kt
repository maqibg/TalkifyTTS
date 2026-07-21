package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * MiniMax 语音合成供应商配置
 *
 * 继承 [BaseProviderConfig]，封装 MiniMax 供应商所需的配置信息
 * 使用 MiniMax 服务的 API Key 进行认证
 *
 * @property voiceId 声音 ID，如 "male-qn-qingse"
 * @property apiUrl 自定义 WebSocket API 地址，为空时使用默认地址
 *                   （默认：wss://api.minimaxi.com/ws/v1/t2a_v2）
 * @property modelId 自定义模型 ID，为空时使用默认模型（默认：speech-2.8-turbo）
 * @property apiKey MiniMax 平台的 API Key，用于认证
 *                  从 MiniMax 开放平台获取
 */
data class MiniMaxConfig(
    override val voiceId: String = "",
    override val apiUrl: String = "",
    override val modelId: String = "",
    val apiKey: String = "",
    val continuousSound: Boolean = true
) : BaseProviderConfig(voiceId, apiUrl, modelId)
