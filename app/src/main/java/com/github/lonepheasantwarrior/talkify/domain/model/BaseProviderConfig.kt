package com.github.lonepheasantwarrior.talkify.domain.model

/**
 * TTS 供应商配置基类
 *
 * 定义所有 TTS 供应商配置的通用结构
 * 采用抽象基类设计，便于扩展不同供应商的特定配置
 *
 * 设计原则：
 * - 只有所有供应商共有的属性才放在基类中
 * - 具体供应商的特有属性由子类定义
 *
 * @property voiceId 声音 ID，跨供应商通用属性
 *                   不同供应商对 voiceId 的格式和含义可能有不同要求
 * @property apiUrl 自定义 API 地址，为空时使用供应商默认地址
 *                   允许用户将请求指向自建代理、私有化部署或兼容服务
 * @property modelId 自定义模型 ID，为空时使用供应商默认模型
 *                    允许用户切换同供应商下的不同模型版本
 */
abstract class BaseProviderConfig(
    open val voiceId: String = "",
    open val apiUrl: String = "",
    open val modelId: String = ""
)
