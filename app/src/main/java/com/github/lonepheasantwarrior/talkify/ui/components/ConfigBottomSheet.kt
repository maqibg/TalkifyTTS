package com.github.lonepheasantwarrior.talkify.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.BaseProviderConfig
import com.github.lonepheasantwarrior.talkify.domain.model.ConfigItem
import com.github.lonepheasantwarrior.talkify.domain.model.MicrosoftTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MiniMaxTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.Qwen3TtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.SeedTts2Config
import com.github.lonepheasantwarrior.talkify.domain.model.TencentTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.TtsProvider
import com.github.lonepheasantwarrior.talkify.domain.model.XiaoMiMimoConfig
import com.github.lonepheasantwarrior.talkify.domain.repository.ProviderConfigRepository
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceInfo
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository
import com.github.lonepheasantwarrior.talkify.service.provider.TtsProviderApi
import com.github.lonepheasantwarrior.talkify.service.provider.TtsProviderFactory

/**
 * 配置底部弹窗
 *
 * 展示供应商配置编辑界面，包含 API Key 输入和声音选择
 * 通过右下角悬浮按钮唤出
 *
 * 支持多供应商架构，每个供应商可以定义自己的配置项
 * 使用供应商的 [TtsProviderApi.createDefaultConfig] 方法动态创建正确的配置类型
 * 使用供应商的 [TtsProviderApi.getConfigLabel] 方法获取本地化的配置项标签
 *
 * @param modifier 修饰符
 * @param isOpen 是否展开弹窗
 * @param onDismiss 关闭弹窗的回调
 * @param currentProvider 当前选中的供应商
 * @param configRepository 配置仓储
 * @param voiceRepository 声音仓储
 * @param onConfigSaved 配置保存后的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigBottomSheet(
    modifier: Modifier = Modifier,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    currentProvider: TtsProvider,
    configRepository: ProviderConfigRepository,
    voiceRepository: VoiceRepository,
    onConfigSaved: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val context = LocalContext.current

    LaunchedEffect(isOpen) {
        if (!isOpen && sheetState.isVisible) {
            sheetState.hide()
        }
    }

    val savedConfig = remember(currentProvider, isOpen) {
        configRepository.getConfig(currentProvider.id)
    }

    val provider = remember(currentProvider.id) {
        TtsProviderFactory.createProvider(currentProvider.id)
    }

    val defaultConfig = remember(currentProvider.id) {
        provider?.createDefaultConfig() ?: throw IllegalStateException("Provider not found: ${currentProvider.id}")
    }

    val configForEdit: BaseProviderConfig = remember(savedConfig, defaultConfig) {
        when (defaultConfig) {
            is Qwen3TtsConfig -> {
                val qwenSaved = savedConfig as? Qwen3TtsConfig
                qwenSaved ?: defaultConfig
            }
            is SeedTts2Config -> {
                val seedSaved = savedConfig as? SeedTts2Config
                seedSaved ?: defaultConfig
            }
            is TencentTtsConfig -> {
                val tencentSaved = savedConfig as? TencentTtsConfig
                tencentSaved ?: defaultConfig
            }
            is MicrosoftTtsConfig -> {
                val msSaved = savedConfig as? MicrosoftTtsConfig
                msSaved ?: defaultConfig
            }
            is XiaoMiMimoConfig -> {
                val mmSaved = savedConfig as? XiaoMiMimoConfig
                mmSaved ?: defaultConfig
            }
            is MiniMaxTtsConfig -> {
                val mmSaved = savedConfig as? MiniMaxTtsConfig
                mmSaved ?: defaultConfig
            }
            else -> defaultConfig
        }
    }

    val getLabel: (String) -> String? = remember(provider) {
        { key: String ->
            provider?.getConfigLabel(key, context)
        }
    }

    val defaultApiUrl = remember(currentProvider.id) {
        provider?.getDefaultApiUrl() ?: ""
    }
    val defaultModelId = remember(currentProvider.id) {
        provider?.getDefaultModelId() ?: ""
    }

    var configItems by remember(currentProvider, configForEdit, isOpen, getLabel) {
        mutableStateOf(
            buildConfigItems(configForEdit, getLabel, defaultApiUrl, defaultModelId)
        )
    }

    var availableVoices by remember(currentProvider, isOpen) {
        mutableStateOf<List<VoiceInfo>>(emptyList())
    }
    var isVoicesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentProvider, isOpen) {
        isVoicesLoading = true
        try {
            availableVoices = voiceRepository.getVoicesForProvider(currentProvider)
        } finally {
            isVoicesLoading = false
        }
    }

    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (isVoicesLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.voice_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ConfigEditor(
                    providerName = currentProvider.name,
                    configItems = configItems,
                    availableVoices = availableVoices,
                    onItemValueChange = { changedItem, newValue ->
                        configItems = configItems.map {
                            if (it.key == changedItem.key) it.copy(value = newValue) else it
                        }
                    },
                    onSaveClick = {
                        val newConfig = buildConfigFromItems(
                            configItems,
                            defaultConfig
                        )
                        configRepository.saveConfig(currentProvider.id, newConfig)
                        onConfigSaved?.invoke()
                        onDismiss()
                    },
                    onVoiceSelected = { voice ->
                        val voiceItem = configItems.find { it.key == "voice_id" }
                        if (voiceItem != null) {
                            configItems = configItems.map {
                                if (it.key == "voice_id") it.copy(value = voice.voiceId) else it
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                }
            }
        }
    }
}

private fun buildConfigItems(
    config: BaseProviderConfig,
    getLabel: (String) -> String?,
    defaultApiUrl: String,
    defaultModelId: String
): List<ConfigItem> {
    val items = mutableListOf<ConfigItem>()

    // API 地址（仅当供应商支持自定义时展示，placeholder 显示默认值）
    if (defaultApiUrl.isNotEmpty()) {
        val apiUrlLabel = getLabel("api_url")
        if (apiUrlLabel != null) {
            items.add(
                ConfigItem(
                    key = "api_url",
                    label = apiUrlLabel,
                    value = config.apiUrl,
                    placeholder = defaultApiUrl
                )
            )
        }
    }

    // 模型 ID（仅当供应商支持自定义时展示，placeholder 显示默认值）
    if (defaultModelId.isNotEmpty()) {
        val modelIdLabel = getLabel("model_id")
        if (modelIdLabel != null) {
            items.add(
                ConfigItem(
                    key = "model_id",
                    label = modelIdLabel,
                    value = config.modelId,
                    placeholder = defaultModelId
                )
            )
        }
    }

    when (config) {
        is Qwen3TtsConfig -> {
            val label = getLabel("api_key")
            if (label != null) {
                items.add(
                    ConfigItem(
                        key = "api_key",
                        label = label,
                        value = config.apiKey,
                        isPassword = true
                    )
                )
            }
        }
        is SeedTts2Config -> {
            val apiKeyLabel = getLabel("api_key")
            if (apiKeyLabel != null) {
                items.add(
                    ConfigItem(
                        key = "api_key",
                        label = apiKeyLabel,
                        value = config.apiKey,
                        isPassword = true
                    )
                )
            }
        }
        is TencentTtsConfig -> {
            val appIdLabel = getLabel("app_id")
            if (appIdLabel != null) {
                items.add(
                    ConfigItem(
                        key = "app_id",
                        label = appIdLabel,
                        value = config.appId,
                        isPassword = false
                    )
                )
            }
            val secretIdLabel = getLabel("secret_id")
            if (secretIdLabel != null) {
                items.add(
                    ConfigItem(
                        key = "secret_id",
                        label = secretIdLabel,
                        value = config.secretId,
                        isPassword = true
                    )
                )
            }
            val secretKeyLabel = getLabel("secret_key")
            if (secretKeyLabel != null) {
                items.add(
                    ConfigItem(
                        key = "secret_key",
                        label = secretKeyLabel,
                        value = config.secretKey,
                        isPassword = true
                    )
                )
            }
        }
        is MicrosoftTtsConfig -> {
        }
        is XiaoMiMimoConfig -> {
            val label = getLabel("api_key")
            if (label != null) {
                items.add(
                    ConfigItem(
                        key = "api_key",
                        label = label,
                        value = config.apiKey,
                        isPassword = true
                    )
                )
            }
        }
        is MiniMaxTtsConfig -> {
            val label = getLabel("api_key")
            if (label != null) {
                items.add(
                    ConfigItem(
                        key = "api_key",
                        label = label,
                        value = config.apiKey,
                        isPassword = true
                    )
                )
            }
        }
    }

    val voiceLabel = getLabel("voice_id")
    if (voiceLabel != null) {
        items.add(
            ConfigItem(
                key = "voice_id",
                label = voiceLabel,
                value = config.voiceId,
                isVoiceSelector = true
            )
        )
    }

    if (config is MiniMaxTtsConfig) {
        val synthConfigLabel = getLabel("continuous_sound")
        if (synthConfigLabel != null) {
            items.add(
                ConfigItem(
                    key = "continuous_sound",
                    label = synthConfigLabel,
                    value = config.continuousSound.toString(),
                    dropdownOptions = listOf(
                        "true" to "更自然韵律",
                        "false" to "更快速度"
                    )
                )
            )
        }
    }

    return items
}

private fun buildConfigFromItems(
    items: List<ConfigItem>,
    defaultConfig: BaseProviderConfig
): BaseProviderConfig {
    val voiceId = items.find { it.key == "voice_id" }?.value ?: defaultConfig.voiceId
    val apiUrl = items.find { it.key == "api_url" }?.value ?: ""
    val modelId = items.find { it.key == "model_id" }?.value ?: ""

    return when (defaultConfig) {
        is Qwen3TtsConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            Qwen3TtsConfig(
                apiKey = apiKey,
                voiceId = voiceId,
                apiUrl = apiUrl,
                modelId = modelId
            )
        }
        is SeedTts2Config -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            SeedTts2Config(
                apiKey = apiKey,
                voiceId = voiceId,
                apiUrl = apiUrl,
                modelId = modelId
            )
        }
        is TencentTtsConfig -> {
            val appId = items.find { it.key == "app_id" }?.value ?: ""
            val secretId = items.find { it.key == "secret_id" }?.value ?: ""
            val secretKey = items.find { it.key == "secret_key" }?.value ?: ""
            TencentTtsConfig(
                appId = appId,
                secretId = secretId,
                secretKey = secretKey,
                voiceId = voiceId,
                apiUrl = apiUrl,
                modelId = modelId
            )
        }
        is MicrosoftTtsConfig -> {
            MicrosoftTtsConfig(
                voiceId = voiceId,
                apiUrl = apiUrl
            )
        }
        is XiaoMiMimoConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            XiaoMiMimoConfig(
                apiKey = apiKey,
                voiceId = voiceId,
                apiUrl = apiUrl,
                modelId = modelId
            )
        }
        is MiniMaxTtsConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            val continuousSound = items.find { it.key == "continuous_sound" }?.value?.toBooleanStrictOrNull() ?: true
            MiniMaxTtsConfig(
                apiKey = apiKey,
                voiceId = voiceId,
                apiUrl = apiUrl,
                modelId = modelId,
                continuousSound = continuousSound
            )
        }
        else -> defaultConfig
    }
}
