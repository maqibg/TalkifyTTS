package com.github.lonepheasantwarrior.talkify.service.provider.impl

import android.speech.tts.Voice
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.TalkifyAppHolder
import com.github.lonepheasantwarrior.talkify.domain.model.BaseProviderConfig
import com.github.lonepheasantwarrior.talkify.domain.model.ProviderIds
import com.github.lonepheasantwarrior.talkify.domain.model.TencentCloudConfig
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlParser
import com.github.lonepheasantwarrior.talkify.service.TtsErrorCode
import com.github.lonepheasantwarrior.talkify.service.TtsLogger
import com.github.lonepheasantwarrior.talkify.service.provider.AbstractTtsProvider
import com.github.lonepheasantwarrior.talkify.service.provider.AudioConfig
import com.github.lonepheasantwarrior.talkify.service.provider.SynthesisParams
import com.github.lonepheasantwarrior.talkify.service.provider.TextChunkSplitter
import com.github.lonepheasantwarrior.talkify.service.provider.TtsSynthesisListener
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizer
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizerListener
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizerRequest
import com.tencent.cloud.stream.tts.SpeechSynthesizerResponse
import com.tencent.cloud.stream.tts.core.ws.Credential
import com.tencent.cloud.stream.tts.core.ws.SpeechClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID

class TencentCloudProvider : AbstractTtsProvider() {

    companion object {
        const val DEFAULT_VOICE_ID = 101027
        private const val VOICE_NAME_SEPARATOR = "::"

        private const val MAX_TEXT_LENGTH = 300

        private val speechClient = SpeechClient()

        val SUPPORTED_LANGUAGES = arrayOf("zho", "eng")

        private val ERROR_CODE_MAP = mapOf(
            -400 to "客户端参数不能为空",
            -401 to "认证信息不能为空",
            -402 to "请求参数不能为空",
            -403 to "监听器不能为空",
            -404 to "应用ID不能为空",
            -405 to "密钥ID不能为空",
            -406 to "密钥Key不能为空",
            -407 to "启动合成器失败",
            -408 to "发送文本失败",
            -409 to "连接服务器失败",
            -410 to "状态错误",
            3022 to "资源包配额已用尽，请检查您的资源包"
        )

        private fun getFriendlyErrorMessage(code: Int?, originalMessage: String?): String {
            val codeValue = code ?: return "语音合成失败: ${originalMessage ?: "未知错误"}"
            
            val mappedMessage = ERROR_CODE_MAP[codeValue]
            return if (mappedMessage != null) {
                "语音合成失败: $mappedMessage (错误码: $codeValue)"
            } else {
                val message = originalMessage ?: "未知错误"
                "语音合成失败: $message (错误码: $codeValue)"
            }
        }
    }

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isCancelled = false

    @Volatile
    private var hasCompleted = false

    @Volatile
    private var currentSynthesizer: FlowingSpeechSynthesizer? = null

    @Volatile
    private var isFirstChunk = true

    @Volatile
    private var firstErrorMessage: String? = null

    private val voiceSampleRateMap: MutableMap<String, Int> by lazy {
        loadVoiceSampleRatesFromResource()
    }

    private val voiceIds: List<String> by lazy {
        loadVoiceIdsFromResource()
    }

    val audioConfig: AudioConfig
        @JvmName("getAudioConfigProperty") get() = AudioConfig.TENCENT_TTS

    private fun loadVoiceIdsFromResource(): List<String> {
        return loadVoiceIdsFromXml(R.xml.tencent_tts_voices)
    }

    private fun loadVoiceSampleRatesFromResource(): MutableMap<String, Int> {
        val context = TalkifyAppHolder.getContext()
        return if (context != null) {
            try {
                val entries = VoiceXmlParser.parse(context, R.xml.tencent_tts_voices)
                val map = mutableMapOf<String, Int>()
                for (entry in entries) {
                    val rate = parseSampleRate(entry.sampleRate)
                    map[entry.id] = rate
                }
                map
            } catch (e: Exception) {
                TtsLogger.e("Failed to load voice sample rates from resource", throwable = e)
                mutableMapOf()
            }
        } else {
            TtsLogger.w("Context not available, voice sample rates will be empty")
            mutableMapOf()
        }
    }

    private fun parseSampleRate(sampleRateStr: String): Int {
        return try {
            val rates = sampleRateStr.split("/")
                .map { it.trim().lowercase() }
                .mapNotNull { rateStr ->
                    when {
                        rateStr.contains("24k") -> 24000
                        rateStr.contains("16k") -> 16000
                        rateStr.contains("8k") -> 8000
                        else -> null
                    }
                }
            rates.maxOrNull() ?: 16000
        } catch (_: Exception) {
            16000
        }
    }

    private fun getSampleRateForVoice(voiceId: String): Int {
        return voiceSampleRateMap[voiceId] ?: 16000
    }

    override fun getProviderId(): String = ProviderIds.TencentCloud.providerId

    override fun getProviderName(): String = ProviderIds.TencentCloud.provider

    override fun getDefaultModelId(): String = ProviderIds.TencentCloud.defaultModelId

    override fun synthesize(
        text: String, params: SynthesisParams, config: BaseProviderConfig, listener: TtsSynthesisListener
    ) {
        checkNotReleased()

        val tencentConfig = config as? TencentCloudConfig
        if (tencentConfig == null) {
            logError("Invalid config type, expected TencentCloudConfig")
            listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_PROVIDER_NOT_CONFIGURED))
            return
        }

        if (tencentConfig.appId.isEmpty() || tencentConfig.secretId.isEmpty() || tencentConfig.secretKey.isEmpty()) {
            logError("AppID or SecretID or SecretKey is not configured")
            listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_PROVIDER_NOT_CONFIGURED))
            return
        }

        if (text.isEmpty()) {
            logWarning("待朗读文本内容为空")
            listener.onSynthesisCompleted()
            return
        }

        if (!containsReadableText(text)) {
            logWarning("文本不包含可朗读的文字内容")
            listener.onSynthesisCompleted()
            return
        }

        val realVoiceId = extractRealVoiceName(tencentConfig.voiceId) ?: voiceIds.firstOrNull() ?: DEFAULT_VOICE_ID.toString()
        val sampleRate = getSampleRateForVoice(realVoiceId)

        logInfo("Starting synthesis: textLength=${text.length}, voiceId=$realVoiceId, sampleRate=$sampleRate, pitch=${params.pitch}, speechRate=${params.speechRate}")
        logDebug("Audio config: sampleRate=$sampleRate, format=PCM_16BIT, channel=mono")

        isCancelled = false
        hasCompleted = false
        isFirstChunk = true
        firstErrorMessage = null

        val textChunks = TextChunkSplitter.split(text, MAX_TEXT_LENGTH)
        if (textChunks.isEmpty()) {
            listener.onError("文本为空")
            return
        }

        logDebug("Text split into ${textChunks.size} chunks")

        providerScope.launch {
            processChunksSequentially(textChunks, tencentConfig, params, realVoiceId, sampleRate, listener)
        }
    }

    private suspend fun processChunksSequentially(
        chunks: List<String>,
        config: TencentCloudConfig,
        params: SynthesisParams,
        voiceId: String,
        sampleRate: Int,
        listener: TtsSynthesisListener
    ) {
        for ((index, chunk) in chunks.withIndex()) {
            if (isCancelled || hasCompleted) {
                logDebug("Synthesis cancelled or completed, stopping chunk processing")
                return
            }

            logDebug("Processing chunk $index/${chunks.size}, length=${chunk.length}")

            val success = processSingleChunk(chunk, config, params, voiceId, sampleRate, listener)
            if (!success) {
                logError("Failed to process chunk $index")
                return
            }
        }

        if (!isCancelled && !hasCompleted) {
            hasCompleted = true
            withContext(Dispatchers.Main) {
                listener.onSynthesisCompleted()
            }
            logInfo("Synthesis completed successfully")
        }
    }

    private suspend fun processSingleChunk(
        text: String,
        config: TencentCloudConfig,
        params: SynthesisParams,
        voiceId: String,
        sampleRate: Int,
        listener: TtsSynthesisListener
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = Credential(config.appId, config.secretId, config.secretKey, "")
            val request = buildTtsRequest(params, voiceId, sampleRate)

            val chunkStarted = false
            val chunkCompleted = kotlinx.coroutines.CompletableDeferred<Boolean>()
            val hasError = false

            val ttsListener = object : FlowingSpeechSynthesizerListener() {
                override fun onSynthesisStart(response: SpeechSynthesizerResponse?) {
                    logDebug("onSynthesisStart: sessionId=${response?.sessionId}")
                    if (!chunkStarted && isFirstChunk) {
                        isFirstChunk = false
                        listener.onSynthesisStarted()
                    }
                }

                override fun onSynthesisEnd(response: SpeechSynthesizerResponse?) {
                    logDebug("onSynthesisEnd: sessionId=${response?.sessionId}")
                    chunkCompleted.complete(!hasError)
                }

                override fun onAudioResult(buffer: ByteBuffer?) {
                    if (buffer != null && buffer.remaining() > 0) {
                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)
                        logDebug("Received audio chunk: ${data.size} bytes")
                        listener.onAudioAvailable(
                            data,
                            sampleRate,
                            audioConfig.audioFormat,
                            audioConfig.channelCount
                        )
                    }
                }

                override fun onTextResult(response: SpeechSynthesizerResponse?) {
                    logDebug("onTextResult: ${response?.result}")
                }

                override fun onSynthesisCancel() {
                    logDebug("onSynthesisCancel")
                    chunkCompleted.complete(false)
                }

                override fun onSynthesisFail(response: SpeechSynthesizerResponse?) {
                    val errorMsg = response?.message ?: "Unknown error"
                    val errorCode = response?.code
                    logError("onSynthesisFail: $errorMsg, code=$errorCode")
                    
                    if (firstErrorMessage == null) {
                        firstErrorMessage = getFriendlyErrorMessage(errorCode, errorMsg)
                        providerScope.launch(Dispatchers.Main) {
                            listener.onError(firstErrorMessage!!)
                        }
                    }
                    chunkCompleted.complete(false)
                }
            }

            currentSynthesizer = FlowingSpeechSynthesizer(speechClient, credential, request, ttsListener)

            if (isCancelled) {
                return@withContext false
            }

            currentSynthesizer?.start()
            currentSynthesizer?.process(text)
            currentSynthesizer?.stop()

            val success = chunkCompleted.await()

            currentSynthesizer = null

            success
        } catch (e: Exception) {
            logError("Unexpected error during synthesis", e)
            if (firstErrorMessage == null) {
                firstErrorMessage = "语音合成失败: ${e.message ?: "未知错误"}"
                withContext(Dispatchers.Main) {
                    listener.onError(firstErrorMessage!!)
                }
            }
            false
        } finally {
            currentSynthesizer = null
        }
    }

    private fun buildTtsRequest(
        params: SynthesisParams,
        voiceId: String,
        sampleRate: Int
    ): FlowingSpeechSynthesizerRequest {
        val request = FlowingSpeechSynthesizerRequest()

        request.codec = "pcm"
        request.sampleRate = sampleRate
        request.voiceType = voiceId.toIntOrNull() ?: DEFAULT_VOICE_ID
        request.enableSubtitle = false
        request.emotionCategory = "neutral"
        request.emotionIntensity = 100
        request.sessionId = UUID.randomUUID().toString()

        val speed = convertSpeechRate(params.speechRate)
        request.speed = speed
        logDebug("ttsSpeechRate: ${params.speechRate}, tencentSpeed: $speed")

        val volume = convertVolume(params.volume)
        request.volume = volume
        logDebug("ttsVolume: ${params.volume}, tencentVolume: $volume")

        return request
    }

    private fun convertSpeechRate(androidRate: Float): Float {
        return when {
            androidRate <= 50f -> -2f
            androidRate <= 80f -> -1f
            androidRate <= 120f -> 0f
            androidRate <= 150f -> 1f
            androidRate <= 200f -> 2f
            else -> 6f
        }
    }

    private fun convertVolume(androidVolume: Float): Float {
        return when {
            androidVolume <= 0f -> -10f
            androidVolume >= 1f -> 10f
            else -> (androidVolume - 0.5f) * 20f
        }
    }


    override fun getSupportedLanguages(): Set<String> {
        return SUPPORTED_LANGUAGES.toSet()
    }

    override fun getDefaultLanguages(): Array<String> {
        return arrayOf(Locale.SIMPLIFIED_CHINESE.language, Locale.SIMPLIFIED_CHINESE.country, "")
    }

    override fun getSupportedVoices(): List<Voice> {
        val voices = mutableListOf<Voice>()

        for (langCode in getSupportedLanguages()) {
            for (voiceId in voiceIds) {
                voices.add(
                    Voice(
                        "$voiceId$VOICE_NAME_SEPARATOR$langCode",
                        Locale.forLanguageTag(langCode),
                        Voice.QUALITY_NORMAL,
                        Voice.LATENCY_NORMAL,
                        true,
                        emptySet()
                    )
                )
            }
        }
        return voices
    }

    override fun getDefaultVoiceId(
        lang: String?,
        country: String?,
        variant: String?,
        currentVoiceId: String?
    ): String {
        val defaultVoice = voiceIds.firstOrNull() ?: DEFAULT_VOICE_ID.toString()
        if (!currentVoiceId.isNullOrBlank()) {
            return "$currentVoiceId$VOICE_NAME_SEPARATOR$lang"
        }
        return "$defaultVoice$VOICE_NAME_SEPARATOR$lang"
    }

    override fun isVoiceIdCorrect(voiceId: String?): Boolean {
        if (voiceId == null) {
            return false
        }
        val realVoiceName = extractRealVoiceName(voiceId)
        return realVoiceName != null && voiceIds.contains(realVoiceName)
    }


    override fun stop() {
        logInfo("Stopping synthesis")
        isCancelled = true
        currentSynthesizer?.cancel()
        currentSynthesizer = null
    }

    override fun release() {
        logInfo("Releasing provider")
        isCancelled = true
        currentSynthesizer?.cancel()
        currentSynthesizer = null
        providerScope.cancel()
        super.release()
    }

    override fun isConfigured(config: BaseProviderConfig?): Boolean {
        val tencentConfig = config as? TencentCloudConfig
        var result = false
        if (tencentConfig != null) {
            result = tencentConfig.appId.isNotBlank() &&
                    tencentConfig.secretId.isNotBlank() &&
                    tencentConfig.secretKey.isNotBlank()
        }
        TtsLogger.d("$tag: isConfigured = $result")
        return result
    }

    override fun createDefaultConfig(): BaseProviderConfig {
        return TencentCloudConfig()
    }

    override fun getConfigLabel(configKey: String, context: android.content.Context): String? {
        return when (configKey) {
            "app_id" -> context.getString(R.string.tencent_app_id_label)
            "secret_id" -> context.getString(R.string.tencent_secret_id_label)
            "secret_key" -> context.getString(R.string.tencent_secret_key_label)
            else -> super.getConfigLabel(configKey, context)
        }
    }
}
