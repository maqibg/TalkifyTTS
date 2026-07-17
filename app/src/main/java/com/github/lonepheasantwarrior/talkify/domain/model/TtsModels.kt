package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * TTS 供应商数据类
 *
 * @property id 供应商唯一标识符（模型 ID）
 * @property name 供应商展示名称（服务提供商名称，作为选项的主要呈现内容）
 * @property provider 供应商模型 ID（作为选项的次要呈现内容）
 */
data class TtsProvider(
    val id: String,
    val name: String,
    val provider: String
)
