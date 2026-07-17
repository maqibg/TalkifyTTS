package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * TTS 供应商数据类
 *
 * @property id 供应商唯一标识符（模型 ID）
 * @property name 供应商展示名称（取自模型 ID，强调模型本身）
 * @property provider 供应商（服务提供商）名称
 */
data class TtsProvider(
    val id: String,
    val name: String,
    val provider: String
)
