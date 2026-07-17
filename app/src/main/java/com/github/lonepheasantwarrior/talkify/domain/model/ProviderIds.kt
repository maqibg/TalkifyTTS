package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * 供应商 ID 密封类
 *
 * 提供类型安全的供应商标识定义
 * 使用密封类确保只有定义的供应商 ID 可用，防止无效的供应商 ID
 *
 * ID 直接使用云服务的官方产品标识符：
 * - qwen3-tts：阿里云百炼"通义千问3语音合成"服务
 *
 * 设计原则：直接采用云服务提供商的官方服务标识，便于 API 调用对接
 *
 * 字段语义：
 * - [value]：供应商唯一标识符（模型 ID），同时作为展示名称使用，强调模型本身
 * - [provider]：供应商（服务提供商）名称，如"阿里云百炼"、"Azure"
 */
sealed class ProviderIds {
    /**
     * 火山引擎 - 豆包语音合成 2.0
     */
    data object SeedTts2 : ProviderIds() {
        override val value: String = "seed-tts-2.0"
        override val provider: String = "火山引擎"
    }

    /**
     * 腾讯云 - 腾讯语音合成供应商
     *
     * @property value 供应商唯一标识符：tencent-tts（同时作为展示名称）
     * @property provider 服务提供商：腾讯云
     */
    data object TencentTts : ProviderIds() {
        override val value: String = "tencent-tts"
        override val provider: String = "腾讯云"
    }

    /**
     * 阿里云百炼 - 通义千问3语音合成供应商
     *
     * @property value 供应商唯一标识符：qwen3-tts（同时作为展示名称）
     * @property provider 服务提供商：阿里云百炼
     */
    data object Qwen3Tts : ProviderIds() {
        override val value: String = "qwen3-tts"
        override val provider: String = "阿里云百炼"
    }

    /**
     * 微软 - 微软语音合成供应商
     *
     * @property value 供应商唯一标识符：microsoft-tts（同时作为展示名称）
     * @property provider 服务提供商：Azure
     */
    data object MicrosoftTts : ProviderIds() {
        override val value: String = "microsoft-tts"
        override val provider: String = "Azure"
    }

    /**
     * 小米 - MiMo 语音合成供应商
     *
     * @property value 供应商唯一标识符：xiaomi-mimo-tts（同时作为展示名称）
     * @property provider 服务提供商：小米
     */
    data object XiaoMiMimo : ProviderIds() {
        override val value: String = "xiaomi-mimo-tts"
        override val provider: String = "小米"
    }

    /**
     * MiniMax - 语音合成供应商
     *
     * @property value 供应商唯一标识符：minimax-tts（同时作为展示名称）
     * @property provider 服务提供商：MiniMax
     */
    data object MiniMax : ProviderIds() {
        override val value: String = "minimax-tts"
        override val provider: String = "MiniMax"
    }

    /**
     * 供应商唯一标识符（模型 ID），同时作为展示名称使用，强调模型本身
     */
    abstract val value: String

    /**
     * 供应商（服务提供商）名称
     */
    abstract val provider: String

    companion object {
        /**
         * 获取所有定义的供应商 ID 列表
         */
        val entries: List<ProviderIds> by lazy {
            listOf(MicrosoftTts, SeedTts2, TencentTts, Qwen3Tts, XiaoMiMimo, MiniMax)
        }
    }
}

/**
 * 将供应商 ID 转换为 TtsProvider 数据类
 *
 * 注意：[TtsProvider.name] 取自 [ProviderIds.value]（模型 ID），
 * 因为 value 同时充当展示名称，强调模型本身。
 *
 * @return TtsProvider 实例
 */
fun ProviderIds.toTtsProvider(): TtsProvider {
    return TtsProvider(
        id = this.value,
        name = this.value,
        provider = this.provider
    )
}
