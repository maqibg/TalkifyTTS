package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * MiniMax 语言增强模式枚举
 *
 * 对应 MiniMax API 中 language_boost 参数的可选值。
 * 语言增强可以改善指定语言的文本识别与合成效果。
 *
 * @property apiValue 发送到 API 的字符串值
 */
enum class LanguageBoost(val apiValue: String) {
    /** 关闭语言增强 */
    OFF(""),
    /** 自动检测语言并增强 */
    AUTO("auto"),
    /** 强化中文识别与合成 */
    CHINESE("zh"),
    /** 强化英文识别与合成 */
    ENGLISH("en")
}

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
 * @property continuousSound 是否启用车水马龙功能（默认 false，与 API 文档一致）
 * @property languageBoost 语言增强模式（默认 OFF，不增强）
 * @property englishNormalization 是否启用英文文本规范化（默认 false）
 */
data class MiniMaxConfig(
    override val voiceId: String = "",
    override val apiUrl: String = "",
    override val modelId: String = "",
    val apiKey: String = "",
    val continuousSound: Boolean? = null, // null=默认(不传参数), true=更自然韵律, false=更快速度
    val languageBoost: LanguageBoost = LanguageBoost.OFF,
    val englishNormalization: Boolean = false
) : BaseProviderConfig(voiceId, apiUrl, modelId)
