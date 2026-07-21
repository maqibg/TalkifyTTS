package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 腾讯语音合成供应商配置
 *
 * 继承 [BaseProviderConfig]，封装腾讯云语音合成供应商所需的配置信息
 * 包含腾讯云服务的 AppID、SecretID、SecretKey 和语音模型配置
 *
 * 配置项说明：
 * - appId：腾讯云账户的 AppID
 * - secretId：腾讯云 API 的 SecretID
 * - secretKey：腾讯云 API 的 SecretKey
 * - voiceId：声音 ID，格式为"音色ID::语言代码"
 *
 * 使用示例：
 * ```
 * val config = TencentTtsConfig(
 *     appId = "12345678",
 *     secretId = "AKIDxxx",
 *     secretKey = "xxx",
 *     voiceId = "502007::zh-CN"
 * )
 * ```
 *
 * @property voiceId 声音 ID，格式为 "音色ID::语言代码"
 *                   如 "502007::zh-CN"、"502006::en-US" 等
 *                   可用声音列表参考腾讯云语音合成官网文档
 * @property apiUrl 自定义 API 地址（腾讯云使用官方 SDK，此字段保留扩展，默认为空）
 * @property modelId 自定义模型 ID（腾讯云使用音色 ID 区分模型，此字段保留扩展，默认为空）
 * @property appId 腾讯云账户的 AppID
 *                  从腾讯云控制台获取
 * @property secretId 腾讯云 API 的 SecretID
 *                    从腾讯云 API 密钥管理控制台获取
 * @property secretKey 腾讯云 API 的 SecretKey
 *                     从腾讯云 API 密钥管理控制台获取
 */
data class TencentCloudConfig(
    override val voiceId: String = "",
    override val apiUrl: String = "",
    override val modelId: String = "",
    val appId: String = "",
    val secretId: String = "",
    val secretKey: String = ""
) : BaseProviderConfig(voiceId, apiUrl, modelId)
