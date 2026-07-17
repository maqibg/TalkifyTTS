package com.github.lonepheasantwarrior.talkify.domain.repository

import com.github.lonepheasantwarrior.talkify.domain.model.TtsProvider

/**
 * 声音信息数据类
 *
 * @param voiceId 声音唯一标识符，用于 API 调用
 * @param displayName 声音中文显示名称，用于 UI 展示
 * @param group 声音分组，用于 UI 分组显示（可选）
 * @param sampleRate 声音支持的采样率（Hz，可选），用于供应商动态选择音频配置
 */
data class VoiceInfo(
    val voiceId: String,
    val displayName: String,
    val group: String? = null,
    val sampleRate: Int? = null
)

/**
 * 声音仓储接口
 *
 * 定义获取可用声音列表的标准方法
 * 采用接口设计，便于后续接入更多 TTS 供应商服务
 */
interface VoiceRepository {
    /**
     * 根据供应商获取可用的声音列表
     *
     * @param provider TTS 供应商
     * @return 可用的声音列表
     */
    suspend fun getVoicesForProvider(provider: TtsProvider): List<VoiceInfo>
}
