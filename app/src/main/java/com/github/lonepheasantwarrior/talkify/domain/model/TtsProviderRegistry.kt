package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * TTS 供应商注册表
 *
 * 作为所有可用供应商的单一数据源（Single Source of Truth）
 * 集中管理供应商信息，便于扩展和维护
 */
object TtsProviderRegistry {
    private val providerList: List<TtsProvider> by lazy {
        ProviderIds.entries.map { it.toTtsProvider() }
    }

    private val providers: Map<String, TtsProvider> by lazy {
        providerList.associate { it.id to it }
    }

    /**
     * 获取所有可用的供应商列表
     */
    val availableProviders: List<TtsProvider>
        get() = providerList

    /**
     * 根据 ID 获取供应商
     *
     * @param id 供应商 ID
     * @return 供应商实例，未找到时返回 null
     */
    fun getProvider(id: String): TtsProvider? {
        return providers[id]
    }

    /**
     * 获取供应商，未找到时返回默认供应商
     *
     * @param id 供应商 ID，可能为 null
     * @return 供应商实例
     */
    fun getProviderOrDefault(id: String?): TtsProvider {
        if (id == null) return defaultProvider
        return providers[id] ?: defaultProvider
    }

    /**
     * 获取默认供应商
     */
    val defaultProvider: TtsProvider
        get() = ProviderIds.MicrosoftTts.toTtsProvider()

    /**
     * 检查供应商是否已注册
     *
     * @param id 供应商 ID
     * @return 是否已注册
     */
    fun contains(id: String): Boolean {
        return providers.containsKey(id)
    }
}
