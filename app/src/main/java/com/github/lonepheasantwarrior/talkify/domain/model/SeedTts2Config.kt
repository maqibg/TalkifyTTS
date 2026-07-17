package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 豆包语音合成 2.0 供应商配置
 *
 * 继承 [BaseProviderConfig]，封装豆包语音合成供应商所需的配置信息
 * 使用火山引擎服务的 API Key 进行认证
 *
 * @property voiceId 声音 ID，如 "zh_female_vv_uranus_bigtts"
 * @property apiKey 火山引擎平台的 API Key，用于认证
 *                  从火山引擎平台控制台获取
 */
data class SeedTts2Config(
    override val voiceId: String = "",
    val apiKey: String = ""
) : BaseProviderConfig(voiceId)
