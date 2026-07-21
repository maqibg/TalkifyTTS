package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 微软语音合成供应商配置
 *
 * 继承 [BaseProviderConfig]，封装微软语音合成供应商所需的配置信息
 * 注意：微软语音合成无需 API Key，仅需配置音色即可
 *
 * 配置项说明：
 * - voiceId：声音 ID，格式为微软标准格式，如 "zh-CN-XiaoxiaoNeural"
 *
 * 使用示例：
 * ```
 * val config = MicrosoftTtsConfig(
 *     voiceId = "zh-CN-XiaoxiaoNeural"
 * )
 * ```
 *
 * @property voiceId 声音 ID，微软标准格式
 * @property apiUrl 自定义 WebSocket API 地址，为空时使用默认地址
 *                   （默认：wss://speech.platform.bing.com/...）
 */
data class AzureConfig(
    override val voiceId: String = "",
    override val apiUrl: String = ""
) : BaseProviderConfig(voiceId, apiUrl, "")
