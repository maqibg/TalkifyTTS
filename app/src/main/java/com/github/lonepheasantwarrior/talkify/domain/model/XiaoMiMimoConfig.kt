package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 小米 MiMo 语音合成供应商配置
 *
 * 继承 [BaseProviderConfig]，封装小米 MiMo 供应商所需的配置信息
 * 使用小米服务的 API Key 进行认证
 *
 * @property voiceId 声音 ID，如 "mimo_default"
 * @property apiKey 小米平台的 API Key，用于认证
 *                  从小米开放平台获取
 */
data class XiaoMiMimoConfig(
    override val voiceId: String = "",
    val apiKey: String = ""
) : BaseProviderConfig(voiceId)
