package com.github.lonepheasantwarrior.talkify.infrastructure.app.telemetry

import com.aptabase.Aptabase
import com.github.lonepheasantwarrior.talkify.TalkifyAppHolder

/**
 * Talkify 遥测服务
 *
 * 对 [Aptabase] 实例进行统一封装，作为整个应用遥测能力的**唯一入口**。
 * 所有事件上报统一经过此服务，严禁在其他位置直接引用 Aptabase SDK。
 *
 * **自举设计**：
 * - 服务本身持有 AppKey，无需外部传入配置
 * - 通过 [TalkifyAppHolder] 自主获取 Context，不依赖调用方传入
 * - 首次调用 [trackEvent] 时延迟初始化（DCL），调用方无需感知初始化状态
 * - 若 Context 尚未就绪（极端情况）则静默跳过，**遥测绝不引发崩溃**
 *
 * **设计原则**：
 * - **高内聚**：配置、初始化、上报三者闭环在同一个 `object` 内
 * - **单一责任**：集中管理 Aptabase 生命周期，其他模块零感知
 * - **统一入口**：`trackEvent()` 提供一致的调用接口，便于埋点管理、测试与 mock
 * - **零权限**：不额外收集敏感信息，匿名设备信息由 [DeviceInfoCollector] 负责
 *
 * @see Aptabase
 * @see DeviceInfoCollector
 */
object TalkifyTelemetry {

    private const val APTABASE_KEY = "A-US-8713354124"

    @Volatile
    private var initialized = false

    // ==================== 公共 API ====================

    /**
     * 上报一个简单事件
     *
     * 首次调用会自动完成 SDK 初始化，调用方无需关心初始化状态。
     *
     * 使用示例：
     * ```kotlin
     * TalkifyTelemetry.trackEvent("settings_viewed")
     * ```
     *
     * @param eventName 事件名称（建议使用 snake_case）
     */
    fun trackEvent(eventName: String) {
        ensureInitialized() || return
        Aptabase.instance.trackEvent(eventName)
    }

    /**
     * 上报一个带自定义属性的事件
     *
     * 首次调用会自动完成 SDK 初始化，调用方无需关心初始化状态。
     *
     * 使用示例：
     * ```kotlin
     * TalkifyTelemetry.trackEvent("tts_started", mapOf(
     *     "engine" to "qwen3",
     *     "stream_mode" to 1
     * ))
     * ```
     *
     * @param eventName  事件名称（建议使用 snake_case）
     * @param properties 自定义属性，仅支持 String 和 Int 类型值
     */
    fun trackEvent(eventName: String, properties: Map<String, Any>) {
        ensureInitialized() || return
        Aptabase.instance.trackEvent(eventName, properties)
    }

    // ==================== 内部实现 ====================

    /**
     * 确保 SDK 已初始化（DCL 双重检查锁定）
     *
     * @return `true` 已初始化或初始化成功，`false` Context 尚未就绪（静默跳过）
     */
    private fun ensureInitialized(): Boolean {
        if (initialized) return true          // 快速路径，无锁

        synchronized(this) {
            if (initialized) return true      // 二次检查，防止重复初始化
            val context = TalkifyAppHolder.getContext() ?: return false
            Aptabase.instance.initialize(context.applicationContext, APTABASE_KEY)
            initialized = true
            return true
        }
    }
}
