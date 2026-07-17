package com.github.lonepheasantwarrior.talkify.service.provider

import android.content.Context
import com.github.lonepheasantwarrior.talkify.domain.repository.ProviderConfigRepository
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.MicrosoftTtsConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.MicrosoftTtsVoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.Qwen3TtsConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.Qwen3TtsVoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.SeedTts2ConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.SeedTts2VoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.TencentTtsConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.TencentTtsVoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.XiaoMiMimoTtsConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.XiaoMiMimoTtsVoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.MiniMaxTtsConfigRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.provider.repo.MiniMaxTtsVoiceRepository
import com.github.lonepheasantwarrior.talkify.service.TtsLogger
import com.github.lonepheasantwarrior.talkify.service.provider.impl.MicrosoftTtsProvider
import com.github.lonepheasantwarrior.talkify.service.provider.impl.Qwen3TtsProvider
import com.github.lonepheasantwarrior.talkify.service.provider.impl.SeedTts2Provider
import com.github.lonepheasantwarrior.talkify.service.provider.impl.TencentTtsProvider
import com.github.lonepheasantwarrior.talkify.service.provider.impl.XiaoMiMimoTtsProvider
import com.github.lonepheasantwarrior.talkify.service.provider.impl.MiniMaxTtsProvider

/**
 * TTS 供应商工厂
 *
 * 核心职责：作为所有供应商相关组件（Provider, ConfigRepo, VoiceRepo）的统一构建入口。
 * 设计目标：实现完全的插件化架构，业务层只需通过 ID 请求组件，无需感知具体实现类。
 */
object TtsProviderFactory {

    /**
     * 供应商组件构建器集合
     * 封装了创建 Provider、ConfigRepo、VoiceRepo 的工厂 lambda
     */
    private data class ComponentFactories(
        val createProvider: () -> TtsProviderApi,
        val createConfigRepo: (Context) -> ProviderConfigRepository,
        val createVoiceRepo: (Context) -> VoiceRepository
    )

    @Volatile
    private var registry: Map<String, ComponentFactories>? = null

    private val lock = Any()

    /**
     * 根据供应商 ID 创建供应商实例
     */
    fun createProvider(providerId: String): TtsProviderApi? {
        val factories = getRegistry()[providerId] ?: run {
            TtsLogger.w("TtsProviderFactory: provider not found - $providerId")
            return null
        }
        return try {
            factories.createProvider()
        } catch (e: Exception) {
            TtsLogger.e("TtsProviderFactory: failed to create provider - $providerId", e)
            null
        }
    }

    /**
     * 创建供应商配置仓储
     */
    fun createConfigRepository(providerId: String, context: Context): ProviderConfigRepository? {
        val factories = getRegistry()[providerId] ?: return null
        return try {
            factories.createConfigRepo(context)
        } catch (e: Exception) {
            TtsLogger.e("TtsProviderFactory: failed to create config repo - $providerId", e)
            null
        }
    }

    /**
     * 创建供应商声音仓储
     */
    fun createVoiceRepository(providerId: String, context: Context): VoiceRepository? {
        val factories = getRegistry()[providerId] ?: return null
        return try {
            factories.createVoiceRepo(context)
        } catch (e: Exception) {
            TtsLogger.e("TtsProviderFactory: failed to create voice repo - $providerId", e)
            null
        }
    }

    fun isRegistered(providerId: String): Boolean {
        return getRegistry().containsKey(providerId)
    }

    // --- 内部注册逻辑 ---

    private fun getRegistry(): Map<String, ComponentFactories> {
        return registry ?: synchronized(lock) {
            registry ?: initializeRegistry().also { registry = it }
        }
    }

    private fun initializeRegistry(): Map<String, ComponentFactories> {
        TtsLogger.d("TtsProviderFactory: initializing registry")
        return mapOf(
            Qwen3TtsProvider.PROVIDER_ID to ComponentFactories(
                createProvider = { Qwen3TtsProvider() },
                createConfigRepo = { ctx -> Qwen3TtsConfigRepository(ctx) },
                createVoiceRepo = { ctx -> Qwen3TtsVoiceRepository(ctx) }
            ),
            SeedTts2Provider.PROVIDER_ID to ComponentFactories(
                createProvider = { SeedTts2Provider() },
                createConfigRepo = { ctx -> SeedTts2ConfigRepository(ctx) },
                createVoiceRepo = { ctx -> SeedTts2VoiceRepository(ctx) }
            ),
            TencentTtsProvider.PROVIDER_ID to ComponentFactories(
                createProvider = { TencentTtsProvider() },
                createConfigRepo = { ctx -> TencentTtsConfigRepository(ctx) },
                createVoiceRepo = { ctx -> TencentTtsVoiceRepository(ctx) }
            ),
            MicrosoftTtsProvider.PROVIDER_ID to ComponentFactories(
                createProvider = { MicrosoftTtsProvider() },
                createConfigRepo = { ctx -> MicrosoftTtsConfigRepository(ctx) },
                createVoiceRepo = { ctx -> MicrosoftTtsVoiceRepository(ctx) }
            ),
            XiaoMiMimoTtsProvider.PROVIDER_ID to ComponentFactories(
                createProvider = { XiaoMiMimoTtsProvider() },
                createConfigRepo = { ctx -> XiaoMiMimoTtsConfigRepository(ctx) },
                createVoiceRepo = { ctx -> XiaoMiMimoTtsVoiceRepository(ctx) }
            ),
            MiniMaxTtsProvider.PROVIDER_ID to ComponentFactories(
                createProvider = { MiniMaxTtsProvider() },
                createConfigRepo = { ctx -> MiniMaxTtsConfigRepository(ctx) },
                createVoiceRepo = { ctx -> MiniMaxTtsVoiceRepository(ctx) }
            )
        ).also {
            TtsLogger.i("TtsProviderFactory: ${it.size} providers registered")
        }
    }
}
