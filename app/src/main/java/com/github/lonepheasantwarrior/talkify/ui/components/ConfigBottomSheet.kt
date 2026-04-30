package com.github.lonepheasantwarrior.talkify.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.BaseEngineConfig
import com.github.lonepheasantwarrior.talkify.domain.model.ConfigItem
import com.github.lonepheasantwarrior.talkify.domain.model.MicrosoftTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MiniMaxTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MiMoTokenPlanConfig
import com.github.lonepheasantwarrior.talkify.domain.model.Qwen3TtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.SeedTts2Config
import com.github.lonepheasantwarrior.talkify.domain.model.TencentTtsConfig
import com.github.lonepheasantwarrior.talkify.domain.model.TtsEngine
import com.github.lonepheasantwarrior.talkify.domain.model.XiaoMiMimoConfig
import com.github.lonepheasantwarrior.talkify.domain.repository.EngineConfigRepository
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceInfo
import com.github.lonepheasantwarrior.talkify.domain.repository.VoiceRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.engine.repo.MiMoTokenPlanPresetRepository
import com.github.lonepheasantwarrior.talkify.infrastructure.engine.repo.VoicePreset
import com.github.lonepheasantwarrior.talkify.service.engine.TtsEngineApi
import com.github.lonepheasantwarrior.talkify.service.engine.TtsEngineFactory
import com.github.lonepheasantwarrior.talkify.service.engine.impl.MiMoTokenPlanTtsEngine
import kotlinx.coroutines.launch

/**
 * 配置底部弹窗
 *
 * 展示引擎配置编辑界面，包含 API Key 输入和声音选择
 * 通过右下角悬浮按钮唤出
 *
 * 支持多引擎架构，每个引擎可以定义自己的配置项
 * 使用引擎的 [TtsEngineApi.createDefaultConfig] 方法动态创建正确的配置类型
 * 使用引擎的 [TtsEngineApi.getConfigLabel] 方法获取本地化的配置项标签
 *
 * @param modifier 修饰符
 * @param isOpen 是否展开弹窗
 * @param onDismiss 关闭弹窗的回调
 * @param currentEngine 当前选中的引擎
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
    currentEngine: TtsEngine,
    configRepository: EngineConfigRepository,
    voiceRepository: VoiceRepository,
    onConfigSaved: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Preset management for MiMoTokenPlanConfig
    val presetRepository = remember { MiMoTokenPlanPresetRepository(context) }
    var presetName by remember { mutableStateOf("") }

    // File picker for voice clone audio
    var pendingFilePickerKey by remember { mutableStateOf<String?>(null) }
    var filePickerResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && pendingFilePickerKey != null) {
            val fileName = "voice_clone_${System.currentTimeMillis()}.audio"
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, fileName)
                    file.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                    filePickerResult = pendingFilePickerKey!! to file.absolutePath
                }
            } catch (_: Exception) {
            }
            pendingFilePickerKey = null
        }
    }

    LaunchedEffect(isOpen) {
        if (!isOpen && sheetState.isVisible) {
            sheetState.hide()
        }
    }

    val savedConfig = remember(currentEngine, isOpen) {
        configRepository.getConfig(currentEngine.id)
    }

    val engine = remember(currentEngine.id) {
        TtsEngineFactory.createEngine(currentEngine.id)
    }

    val defaultConfig = remember(currentEngine.id) {
        engine?.createDefaultConfig() ?: throw IllegalStateException("Engine not found: ${currentEngine.id}")
    }

    val configForEdit: BaseEngineConfig = remember(savedConfig, defaultConfig) {
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
            is MiMoTokenPlanConfig -> {
                val mtpSaved = savedConfig as? MiMoTokenPlanConfig
                mtpSaved ?: defaultConfig
            }
            else -> defaultConfig
        }
    }

    val getLabel: (String) -> String? = remember(engine) {
        { key: String ->
            engine?.getConfigLabel(key, context)
        }
    }

    var configItems by remember(currentEngine, configForEdit, isOpen, getLabel) {
        mutableStateOf(
            buildConfigItems(configForEdit, getLabel)
        )
    }

    // Handle file picker result
    LaunchedEffect(filePickerResult) {
        val result = filePickerResult
        if (result != null) {
            configItems = configItems.map {
                if (it.key == result.first) it.copy(value = result.second) else it
            }
            filePickerResult = null
        }
    }

    var availableVoices by remember(currentEngine, isOpen) {
        mutableStateOf<List<VoiceInfo>>(emptyList())
    }
    var isVoicesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentEngine, isOpen) {
        isVoicesLoading = true
        try {
            availableVoices = voiceRepository.getVoicesForEngine(currentEngine)
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
                    engineName = currentEngine.name,
                    configItems = configItems,
                    availableVoices = availableVoices,
                    onItemValueChange = { changedItem, newValue ->
                        // Handle file picker trigger
                        if (newValue == "__FILE_PICKER__" && changedItem.isFileSelector) {
                            pendingFilePickerKey = changedItem.key
                            filePickerLauncher.launch("audio/*")
                            return@ConfigEditor
                        }
                        val updatedItems = configItems.map {
                            if (it.key == changedItem.key) it.copy(value = newValue) else it
                        }
                        // Rebuild config items when model changes for MiMoTokenPlanConfig
                        if (changedItem.key == "model" && defaultConfig is MiMoTokenPlanConfig) {
                            val tempConfig = buildConfigFromItems(updatedItems, defaultConfig)
                            configItems = buildConfigItems(tempConfig, getLabel)
                        } else {
                            configItems = updatedItems
                        }
                    },
                    onSaveClick = {
                        val newConfig = buildConfigFromItems(
                            configItems,
                            defaultConfig
                        )
                        configRepository.saveConfig(currentEngine.id, newConfig)
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

                // Preset save section for MiMoTokenPlanConfig voicedesign/voiceclone modes
                if (defaultConfig is MiMoTokenPlanConfig) {
                    val currentModel = configItems.find { it.key == "model" }?.value ?: "mimo-v2.5-tts"
                    if (currentModel == "mimo-v2.5-tts-voicedesign" || currentModel == "mimo-v2.5-tts-voiceclone") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = presetName,
                                onValueChange = { presetName = it },
                                label = { Text(stringResource(R.string.preset_name_hint)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (presetName.isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.preset_name_empty)
                                            )
                                        }
                                        return@OutlinedButton
                                    }
                                    val desc = configItems.find { it.key == "voice_design_description" }?.value ?: ""
                                    val audioPath = configItems.find { it.key == "voice_clone_audio" }?.value ?: ""
                                    val preset = VoicePreset(
                                        name = presetName,
                                        type = if (currentModel == "mimo-v2.5-tts-voicedesign") "voicedesign" else "voiceclone",
                                        description = desc,
                                        audioPath = audioPath
                                    )
                                    presetRepository.savePreset(preset)
                                    presetName = ""
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.preset_saved)
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.save_as_preset))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SnackbarHost(snackbarHostState)
                    }
                }
                }
            }
        }
    }
}

private fun buildConfigItems(
    config: BaseEngineConfig,
    getLabel: (String) -> String?
): List<ConfigItem> {
    val items = mutableListOf<ConfigItem>()

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
        is MiMoTokenPlanConfig -> {
            // API Key
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

            // Model selector
            val modelLabel = getLabel("model")
            if (modelLabel != null) {
                items.add(
                    ConfigItem(
                        key = "model",
                        label = modelLabel,
                        value = config.model,
                        isDropdown = true,
                        dropdownOptions = listOf(
                            "mimo-v2.5-tts" to "mimo-v2.5-tts (预置音色)",
                            "mimo-v2.5-tts-voicedesign" to "mimo-v2.5-tts-voicedesign (音色设计)",
                            "mimo-v2.5-tts-voiceclone" to "mimo-v2.5-tts-voiceclone (音色克隆)"
                        )
                    )
                )
            }

            // Voice selector - only for standard mode
            if (config.model == "mimo-v2.5-tts") {
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
            }

            // Voice design description - only for voicedesign mode
            if (config.model == "mimo-v2.5-tts-voicedesign") {
                val vdLabel = getLabel("voice_design_description")
                if (vdLabel != null) {
                    items.add(
                        ConfigItem(
                            key = "voice_design_description",
                            label = vdLabel,
                            value = config.voiceDesignDescription,
                            isTextArea = true
                        )
                    )
                }
            }

            // Voice clone audio - only for voiceclone mode
            if (config.model == "mimo-v2.5-tts-voiceclone") {
                val vcLabel = getLabel("voice_clone_audio")
                if (vcLabel != null) {
                    items.add(
                        ConfigItem(
                            key = "voice_clone_audio",
                            label = vcLabel,
                            value = config.voiceCloneAudioPath,
                            isFileSelector = true
                        )
                    )
                }
            }

            // Pre-generate count - for voicedesign and voiceclone modes
            if (config.model == "mimo-v2.5-tts-voicedesign" || config.model == "mimo-v2.5-tts-voiceclone") {
                val pgLabel = getLabel("pre_generate_count")
                if (pgLabel != null) {
                    items.add(
                        ConfigItem(
                            key = "pre_generate_count",
                            label = pgLabel,
                            value = config.preGenerateCount.toString(),
                            isDropdown = true,
                            dropdownOptions = (0..5).map { it.toString() to if (it == 0) "不预生成" else "${it}段" }
                        )
                    )
                }
            }

            // Style tag, dialect - NOT for voicedesign mode
            // voicedesign: no style control, user message is voice description only
            // standard & voiceclone: support style tags in text + user message for style control
            if (config.model != "mimo-v2.5-tts-voicedesign") {
                // Style tag dropdown
                val styleLabel = getLabel("style_tag")
                if (styleLabel != null) {
                    items.add(
                        ConfigItem(
                            key = "style_tag",
                            label = styleLabel,
                            value = config.styleTag,
                            isDropdown = true,
                            dropdownOptions = listOf("" to "无") +
                                MiMoTokenPlanTtsEngine.STYLE_TAGS.map { it to it }
                        )
                    )
                }

                // Dialect dropdown
                val dialectLabel = getLabel("dialect")
                if (dialectLabel != null) {
                    items.add(
                        ConfigItem(
                            key = "dialect",
                            label = dialectLabel,
                            value = config.dialect,
                            isDropdown = true,
                            dropdownOptions = listOf(
                                "" to "无",
                                "东北话" to "东北话",
                                "四川话" to "四川话",
                                "河南话" to "河南话",
                                "粤语" to "粤语"
                            )
                        )
                    )
                }
            }

            // User message - for ALL modes
            // voicedesign: voice description (required)
            // standard/voiceclone: style control via natural language (optional)
            run {
                val umLabel = getLabel("user_message")
                if (umLabel != null) {
                    items.add(
                        ConfigItem(
                            key = "user_message",
                            label = umLabel,
                            value = config.userMessage,
                            isTextArea = true
                        )
                    )
                }
            }
        }
    }

    // MiMoTokenPlanConfig already handles voice_id conditionally in its when branch
    if (config !is MiMoTokenPlanConfig) {
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
    }

    return items
}

private fun buildConfigFromItems(
    items: List<ConfigItem>,
    defaultConfig: BaseEngineConfig
): BaseEngineConfig {
    val voiceId = items.find { it.key == "voice_id" }?.value ?: defaultConfig.voiceId

    return when (defaultConfig) {
        is Qwen3TtsConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            Qwen3TtsConfig(
                apiKey = apiKey,
                voiceId = voiceId
            )
        }
        is SeedTts2Config -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            SeedTts2Config(
                apiKey = apiKey,
                voiceId = voiceId
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
                voiceId = voiceId
            )
        }
        is MicrosoftTtsConfig -> {
            MicrosoftTtsConfig(
                voiceId = voiceId
            )
        }
        is XiaoMiMimoConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            XiaoMiMimoConfig(
                apiKey = apiKey,
                voiceId = voiceId
            )
        }
        is MiniMaxTtsConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            MiniMaxTtsConfig(
                apiKey = apiKey,
                voiceId = voiceId
            )
        }
        is MiMoTokenPlanConfig -> {
            val apiKey = items.find { it.key == "api_key" }?.value ?: ""
            val model = items.find { it.key == "model" }?.value ?: "mimo-v2.5-tts"
            val voiceDesignDesc = items.find { it.key == "voice_design_description" }?.value ?: ""
            val voiceCloneAudio = items.find { it.key == "voice_clone_audio" }?.value ?: ""
            val styleTag = items.find { it.key == "style_tag" }?.value ?: ""
            val dialect = items.find { it.key == "dialect" }?.value ?: ""
            val userMessage = items.find { it.key == "user_message" }?.value ?: ""
            val preGenCount = items.find { it.key == "pre_generate_count" }?.value?.toIntOrNull() ?: 0
            MiMoTokenPlanConfig(
                voiceId = voiceId,
                apiKey = apiKey,
                model = model,
                voiceDesignDescription = voiceDesignDesc,
                voiceCloneAudioPath = voiceCloneAudio,
                styleTag = styleTag,
                dialect = dialect,
                userMessage = userMessage,
                preGenerateCount = preGenCount
            )
        }
        else -> defaultConfig
    }
}
