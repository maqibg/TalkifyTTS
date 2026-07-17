package com.github.lonepheasantwarrior.talkify.domain.repository

import com.github.lonepheasantwarrior.talkify.domain.model.BaseProviderConfig

/**
 * 供应商配置仓储接口
 *
 * 定义供应商配置存取的标准方法
 * 采用接口设计，解耦配置存储与业务逻辑
 * 支持多供应商配置隔离存储
 *
 * 注意：全局配置（如"选择的供应商"）请使用 [AppConfigRepository]
 */
interface ProviderConfigRepository {
    /**
     * 获取指定供应商的配置
     *
     * @param providerId 供应商 ID
     * @return 供应商配置
     */
    fun getConfig(providerId: String): BaseProviderConfig

    /**
     * 保存指定供应商的配置
     *
     * @param providerId 供应商 ID
     * @param config 供应商配置
     */
    fun saveConfig(providerId: String, config: BaseProviderConfig)

    /**
     * 检查指定供应商是否有已保存的配置
     *
     * @param providerId 供应商 ID
     * @return 是否有已保存的配置
     */
    fun hasConfig(providerId: String): Boolean
}
