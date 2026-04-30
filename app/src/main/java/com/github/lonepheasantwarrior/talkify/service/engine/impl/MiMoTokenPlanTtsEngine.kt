package com.github.lonepheasantwarrior.talkify.service.engine.impl

import android.speech.tts.Voice
import android.util.Base64
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.TalkifyAppHolder
import com.github.lonepheasantwarrior.talkify.domain.model.BaseEngineConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MiMoTokenPlanConfig
import com.github.lonepheasantwarrior.talkify.service.TtsErrorCode
import com.github.lonepheasantwarrior.talkify.service.TtsLogger
import com.github.lonepheasantwarrior.talkify.service.engine.AbstractTtsEngine
import com.github.lonepheasantwarrior.talkify.service.engine.AudioConfig
import com.github.lonepheasantwarrior.talkify.service.engine.SynthesisParams
import com.github.lonepheasantwarrior.talkify.service.engine.TtsSynthesisListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit

class MiMoTokenPlanTtsEngine : AbstractTtsEngine() {

    companion object {
        const val ENGINE_ID = "mimo-tokenplan-tts"
        const val ENGINE_NAME = "小米MiMo Token Plan"
        private const val VOICE_NAME_SEPARATOR = "::"
        private const val API_URL = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"

        private const val MODEL_STANDARD = "mimo-v2.5-tts"
        private const val MODEL_VOICE_DESIGN = "mimo-v2.5-tts-voicedesign"
        private const val MODEL_VOICE_CLONE = "mimo-v2.5-tts-voiceclone"

        private const val MAX_TEXT_LENGTH = 300

        private val connectionPool = ConnectionPool(5, 45, TimeUnit.SECONDS)

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        val SUPPORTED_LANGUAGES = arrayOf("zho", "eng")

        val STYLE_TAGS = listOf(
            "开心", "悲伤", "愤怒", "恐惧", "惊讶", "兴奋", "委屈", "平静", "冷漠",
            "怅然", "欣慰", "无奈", "愧疚", "释然", "嫉妒", "厌倦", "忐忑", "动情",
            "温柔", "高冷", "活泼", "严肃", "慵懒", "俏皮", "深沉", "干练", "凌厉",
            "磁性", "醇厚", "清亮", "空灵", "稚嫩", "苍老", "甜美", "沙哑", "醇雅",
            "夹子音", "御姐音", "正太音", "大叔音", "台湾腔",
            "东北话", "四川话", "河南话", "粤语",
            "孙悟空", "林黛玉", "唱歌"
        )

        private val DIALECTS = listOf("", "东北话", "四川话", "河南话", "粤语")
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isCancelled = false

    @Volatile
    private var hasCompleted = false

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var isFirstChunk = true

    @Volatile
    private var currentChannel: Channel<ChunkResult>? = null

    // Cached base64 for voiceclone mode to avoid re-encoding per chunk
    @Volatile
    private var cachedCloneAudioBase64: String? = null
    private var cachedCloneAudioPath: String? = null

    val audioConfig: AudioConfig
        @JvmName("getAudioConfigProperty") get() = AudioConfig.MIMO_TOKENPLAN_TTS

    private val voiceIds: List<String> by lazy {
        loadVoiceIdsFromResource()
    }

    private fun loadVoiceIdsFromResource(): List<String> {
        val context = TalkifyAppHolder.getContext()
        return if (context != null) {
            try {
                context.resources.getStringArray(R.array.mimo_tokenplan_voices).toList()
            } catch (e: Exception) {
                TtsLogger.e("Failed to load voice IDs from resource", throwable = e)
                emptyList()
            }
        } else {
            TtsLogger.w("Context not available, voice IDs will be empty")
            emptyList()
        }
    }

    override fun getEngineId(): String = ENGINE_ID

    override fun getEngineName(): String = ENGINE_NAME

    override fun getAudioConfig(): AudioConfig = audioConfig

    override fun synthesize(
        text: String, params: SynthesisParams, config: BaseEngineConfig, listener: TtsSynthesisListener
    ) {
        checkNotReleased()

        val mimoConfig = config as? MiMoTokenPlanConfig
        if (mimoConfig == null) {
            logError("Invalid config type, expected MiMoTokenPlanConfig")
            listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_ENGINE_NOT_CONFIGURED))
            return
        }

        if (mimoConfig.apiKey.isEmpty()) {
            logError("API Key is not configured")
            listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_ENGINE_NOT_CONFIGURED))
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

        // Validate voice clone audio if in clone mode
        if (mimoConfig.model == MODEL_VOICE_CLONE && mimoConfig.voiceCloneAudioPath.isEmpty()) {
            logError("Voice clone mode requires an audio file")
            listener.onError("请先配置音色克隆音频文件")
            return
        }

        // Validate voice design description if in design mode
        if (mimoConfig.model == MODEL_VOICE_DESIGN && mimoConfig.voiceDesignDescription.isEmpty()) {
            logError("Voice design mode requires a description")
            listener.onError("请先填写音色描述")
            return
        }

        // Build the final text with style tags
        val finalText = buildFinalText(text, mimoConfig)

        logInfo("Starting synthesis: model=${mimoConfig.model}, textLength=${finalText.length}")
        logDebug("Audio config: ${audioConfig.getFormatDescription()}")

        isCancelled = false
        hasCompleted = false
        isFirstChunk = true

        val textChunks = splitTextIntoChunks(finalText, MAX_TEXT_LENGTH)
        if (textChunks.isEmpty()) {
            listener.onError("文本为空")
            return
        }

        logDebug("Text split into ${textChunks.size} chunks")

        engineScope.launch {
            if (mimoConfig.preGenerateCount > 0 && textChunks.size > 1) {
                processChunksWithPreGeneration(textChunks, mimoConfig, params, listener)
            } else {
                processChunksSequentially(textChunks, mimoConfig, params, listener)
            }
        }
    }

    private fun buildFinalText(text: String, config: MiMoTokenPlanConfig): String {
        // VoiceDesign: no style tags (user message is voice description only)
        // Standard & VoiceClone: style tags prepended to assistant content
        if (config.model == MODEL_VOICE_DESIGN) return text

        val tags = mutableListOf<String>()
        if (config.styleTag.isNotEmpty()) {
            tags.add(config.styleTag)
        }
        if (config.dialect.isNotEmpty()) {
            tags.add(config.dialect)
        }

        return if (tags.isNotEmpty()) {
            "(${tags.joinToString(" ")})$text"
        } else {
            text
        }
    }

    private suspend fun processChunksSequentially(
        chunks: List<String>,
        config: MiMoTokenPlanConfig,
        params: SynthesisParams,
        listener: TtsSynthesisListener
    ) {
        for ((index, chunk) in chunks.withIndex()) {
            if (isCancelled || hasCompleted) {
                logDebug("Synthesis cancelled or completed, stopping chunk processing")
                return
            }

            logDebug("Processing chunk $index/${chunks.size}, length=${chunk.length}")

            val success = processSingleChunk(chunk, index, chunks.size, config, params, listener)
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

    private data class ChunkResult(
        val index: Int,
        val audioChunks: List<ByteArray>,
        val success: Boolean,
        val errorMessage: String? = null
    )

    private suspend fun processChunksWithPreGeneration(
        chunks: List<String>,
        config: MiMoTokenPlanConfig,
        params: SynthesisParams,
        listener: TtsSynthesisListener
    ) {
        val preGenCount = config.preGenerateCount.coerceIn(1, chunks.size)
        logInfo("Using pre-generation: preGenCount=$preGenCount, totalChunks=${chunks.size}")

        val channel = Channel<ChunkResult>(preGenCount)
        currentChannel = channel

        // Producer: launch chunk synthesis concurrently with backpressure via channel capacity
        val producerJob = engineScope.launch {
            val activeJobs = mutableListOf<kotlinx.coroutines.Job>()
            for ((index, chunk) in chunks.withIndex()) {
                if (isCancelled) break
                val job = launch {
                    if (isCancelled) return@launch
                    val result = synthesizeChunkToBuffer(chunk, index, chunks.size, config, params)
                    if (!isCancelled) {
                        channel.send(result)
                    }
                }
                activeJobs.add(job)
                // Backpressure: wait for oldest job if we've filled the window
                if (activeJobs.size >= preGenCount) {
                    activeJobs.first().join()
                    activeJobs.removeAt(0)
                }
            }
            // Wait for remaining jobs before closing channel
            activeJobs.forEach { it.join() }
            if (!isCancelled) channel.close()
        }

        // Consumer: receive results in order and send audio to listener
        try {
            for (result in channel) {
                if (isCancelled || hasCompleted) break

                if (!result.success) {
                    logError("Failed to synthesize chunk ${result.index}: ${result.errorMessage}")
                    withContext(Dispatchers.Main) {
                        listener.onError(result.errorMessage ?: TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_SYNTHESIS_FAILED))
                    }
                    hasCompleted = true
                    break
                }

                // Send pre-generated audio data to listener
                for (audioData in result.audioChunks) {
                    if (isCancelled) break
                    if (isFirstChunk) {
                        isFirstChunk = false
                        withContext(Dispatchers.Main) {
                            listener.onSynthesisStarted()
                        }
                    }
                    listener.onAudioAvailable(
                        audioData,
                        audioConfig.sampleRate,
                        audioConfig.audioFormat,
                        audioConfig.channelCount
                    )
                }
                logDebug("Sent pre-generated audio for chunk ${result.index}")
            }
        } finally {
            producerJob.cancel()
            currentChannel = null
        }

        if (!isCancelled && !hasCompleted) {
            hasCompleted = true
            withContext(Dispatchers.Main) {
                listener.onSynthesisCompleted()
            }
            logInfo("Pre-generation synthesis completed successfully")
        }
    }

    private suspend fun synthesizeChunkToBuffer(
        text: String,
        chunkIndex: Int,
        totalChunks: Int,
        config: MiMoTokenPlanConfig,
        params: SynthesisParams
    ): ChunkResult = withContext(Dispatchers.IO) {
        val audioChunks = mutableListOf<ByteArray>()
        try {
            val request = buildHttpRequest(text, config, params)
            val call = sharedClient.newCall(request)
            val response = call.execute()
            logDebug("Pre-gen chunk $chunkIndex - HTTP Response Code: ${response.code}, Headers: ${response.headers}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                logError("HTTP error in pre-gen chunk $chunkIndex: ${response.code}, body: $errorBody")
                val errorMsg = parseError(errorBody)
                response.close()
                return@withContext ChunkResult(chunkIndex, emptyList(), false, errorMsg)
            }

            val body = response.body
            if (body == null) {
                logError("Response body is null for pre-gen chunk $chunkIndex")
                return@withContext ChunkResult(chunkIndex, emptyList(), false)
            }

            body.source().use { source ->
                while (!source.exhausted() && !isCancelled) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") break

                    try {
                        val json = JSONObject(data)
                        if (json.has("error")) {
                            val errorObj = json.getJSONObject("error")
                            val errMsg = errorObj.optString("message", "Unknown error")
                            logError("API error in pre-gen chunk $chunkIndex: $errMsg")
                            return@withContext ChunkResult(chunkIndex, emptyList(), false, errMsg)
                        }
                        val audioData = extractAudioFromSSE(json)
                        if (audioData != null && audioData.isNotEmpty()) {
                            audioChunks.add(audioData)
                        }
                    } catch (e: Exception) {
                        logError("Failed to parse SSE data in pre-gen chunk $chunkIndex", e)
                    }
                }
            }
            response.close()

            logDebug("Pre-gen chunk $chunkIndex: buffered ${audioChunks.size} audio segments")
            ChunkResult(chunkIndex, audioChunks, true)

        } catch (e: SocketTimeoutException) {
            logError("Network timeout in pre-gen chunk $chunkIndex", e)
            ChunkResult(chunkIndex, emptyList(), false)
        } catch (e: IOException) {
            logError("Network error in pre-gen chunk $chunkIndex", e)
            ChunkResult(chunkIndex, emptyList(), false)
        } catch (e: Exception) {
            logError("Unexpected error in pre-gen chunk $chunkIndex", e)
            ChunkResult(chunkIndex, emptyList(), false)
        }
    }

    private suspend fun processSingleChunk(
        text: String,
        chunkIndex: Int,
        totalChunks: Int,
        config: MiMoTokenPlanConfig,
        params: SynthesisParams,
        listener: TtsSynthesisListener
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = buildHttpRequest(text, config, params)

            currentCall = sharedClient.newCall(request)

            val response = currentCall?.execute()
            if (response == null) {
                logError("Failed to execute HTTP request")
                withContext(Dispatchers.Main) {
                    listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_NETWORK_UNAVAILABLE))
                }
                return@withContext false
            }

            logDebug("HTTP Response Code: ${response.code}")
            logDebug("HTTP Response Headers: ${response.headers}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                logError("HTTP error: ${response.code}, body: $errorBody")

                val errorMessage = parseError(errorBody)

                withContext(Dispatchers.Main) {
                    listener.onError(errorMessage)
                }
                response.close()
                return@withContext false
            }

            processStreamResponse(response, chunkIndex, listener)

        } catch (e: SocketTimeoutException) {
            logError("Network timeout", e)
            withContext(Dispatchers.Main) {
                listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_NETWORK_TIMEOUT))
            }
            false
        } catch (e: IOException) {
            logError("Network error", e)
            withContext(Dispatchers.Main) {
                listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_NETWORK_UNAVAILABLE))
            }
            false
        } catch (e: Exception) {
            logError("Unexpected error during synthesis", e)
            withContext(Dispatchers.Main) {
                listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_SYNTHESIS_FAILED))
            }
            false
        } finally {
            currentCall = null
        }
    }

    private suspend fun processStreamResponse(
        response: Response,
        chunkIndex: Int,
        listener: TtsSynthesisListener
    ): Boolean = withContext(Dispatchers.IO) {
        val body = response.body
        if (body == null) {
            logError("Response body is null")
            return@withContext false
        }

        var hasError = false

        try {
            body.source().use { source ->
                while (!source.exhausted() && !isCancelled) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue

                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank()) continue

                    if (data == "[DONE]") {
                        logDebug("Stream completed for chunk $chunkIndex")
                        break
                    }

                    try {
                        val json = JSONObject(data)

                        if (json.has("error")) {
                            val errorObj = json.getJSONObject("error")
                            val errMsg = errorObj.optString("message", "Unknown error")
                            logError("API error: $errMsg")
                            hasError = true
                            withContext(Dispatchers.Main) {
                                listener.onError(errMsg)
                            }
                            break
                        }

                        val audioData = extractAudioFromSSE(json)
                        if (audioData != null && audioData.isNotEmpty()) {
                            if (isFirstChunk) {
                                isFirstChunk = false
                                withContext(Dispatchers.Main) {
                                    listener.onSynthesisStarted()
                                }
                            }

                            listener.onAudioAvailable(
                                audioData,
                                audioConfig.sampleRate,
                                audioConfig.audioFormat,
                                audioConfig.channelCount
                            )
                            logDebug("Received audio data: ${audioData.size} bytes")
                        }

                    } catch (e: Exception) {
                        logError("Failed to parse SSE data: $data", e)
                    }
                }
            }
        } catch (e: Exception) {
            logError("Error reading response stream", e)
            hasError = true
        } finally {
            response.close()
        }

        !hasError
    }

    private fun extractAudioFromSSE(json: JSONObject): ByteArray? {
        return try {
            if (json.has("choices")) {
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    if (choice.has("delta")) {
                        val delta = choice.getJSONObject("delta")
                        if (delta.has("audio")) {
                            val audioObj = delta.get("audio")
                            if (audioObj is JSONObject) {
                                val audioData = audioObj.optString("data")
                                if (audioData.isNotBlank()) {
                                    return Base64.decode(audioData, Base64.DEFAULT)
                                }
                            }
                        }
                    }
                }
            }

            if (json.has("audio")) {
                val audioObj = json.get("audio")
                if (audioObj is String) {
                    return Base64.decode(audioObj, Base64.DEFAULT)
                } else if (audioObj is JSONObject) {
                    val audioData = audioObj.optString("data")
                    if (audioData.isNotBlank()) {
                        return Base64.decode(audioData, Base64.DEFAULT)
                    }
                }
            }

            null
        } catch (e: Exception) {
            logError("Failed to extract audio data", e)
            null
        }
    }

    private fun buildHttpRequest(
        text: String,
        config: MiMoTokenPlanConfig,
        params: SynthesisParams
    ): Request {
        val messages = JSONArray()

        // Add user message for style control (optional for standard/clone, required for voicedesign)
        when (config.model) {
            MODEL_VOICE_DESIGN -> {
                // Voice design: user message is the voice description
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", config.voiceDesignDescription)
                })
            }
            MODEL_STANDARD, MODEL_VOICE_CLONE -> {
                // Add user message for style control if provided
                if (config.userMessage.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", config.userMessage)
                    })
                }
            }
        }

        // Add assistant message with the text to synthesize
        messages.put(JSONObject().apply {
            put("role", "assistant")
            put("content", text)
        })

        // Build audio config
        val audioObj = JSONObject().apply {
            put("format", "pcm16")
            when (config.model) {
                MODEL_STANDARD -> {
                    val voiceId = resolveVoiceId(config, params.language)
                    put("voice", voiceId)
                }
                MODEL_VOICE_DESIGN -> {
                    // No voice field for voice design
                }
                MODEL_VOICE_CLONE -> {
                    // Voice is base64-encoded audio file
                    val audioBase64 = getOrLoadAudioBase64(config.voiceCloneAudioPath)
                    if (audioBase64 != null) {
                        val mimeType = detectMimeType(config.voiceCloneAudioPath)
                        put("voice", "data:$mimeType;base64,$audioBase64")
                    }
                }
            }
        }

        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("audio", audioObj)
            put("stream", true)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .header("api-key", config.apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Connection", "keep-alive")
            .build()

        logDebug("HTTP Request URL: ${request.url}")
        logDebug("HTTP Request Headers (masked): ${request.headers.toMaskedString()}")
        logDebug("HTTP Request Body: ${requestBody.toString(2)}")

        return request
    }

    private fun resolveVoiceId(config: MiMoTokenPlanConfig, language: String? = null): String {
        val voiceId = if (config.voiceId.isNotEmpty()) {
            extractRealVoiceName(config.voiceId) ?: config.voiceId
        } else {
            voiceIds.firstOrNull() ?: "mimo_default"
        }
        return resolveVoiceForLanguage(voiceId, language)
    }

    private fun resolveVoiceForLanguage(voiceId: String, language: String?): String {
        if (voiceId.isNotBlank() && voiceIds.contains(voiceId)) {
            return voiceId
        }
        return when (language?.lowercase()) {
            "zh", "zho", "chi", "cn" -> voiceIds.firstOrNull() ?: "mimo_default"
            "en", "eng" -> voiceIds.getOrNull(1) ?: voiceIds.firstOrNull() ?: "mimo_default"
            else -> voiceId.ifBlank { voiceIds.firstOrNull() ?: "mimo_default" }
        }
    }

    private fun getOrLoadAudioBase64(filePath: String): String? {
        val cached = cachedCloneAudioBase64
        if (cached != null && cachedCloneAudioPath == filePath) return cached
        val loaded = loadAudioAsBase64(filePath) ?: return null
        cachedCloneAudioBase64 = loaded
        cachedCloneAudioPath = filePath
        return loaded
    }

    private fun loadAudioAsBase64(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                logError("Voice clone audio file not found: $filePath")
                return null
            }
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            logError("Failed to read voice clone audio file", e)
            null
        }
    }

    private fun detectMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            filePath.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun okhttp3.Headers.toMaskedString(): String {
        val sb = StringBuilder("{")
        for (i in 0 until this.size) {
            val name = this.name(i)
            val value = this.value(i)
            val maskedValue = when (name.lowercase()) {
                "api-key" -> "${value.take(4)}****${value.takeLast(4)}"
                else -> value
            }
            sb.append("$name=$maskedValue")
            if (i < this.size - 1) sb.append(", ")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun parseError(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            val message = json.optString("error", "")
            if (message.isNotBlank()) {
                return message
            }
            json.optString("detail", json.optString("message", TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_SYNTHESIS_FAILED)))
        } catch (_: Exception) {
            TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_SYNTHESIS_FAILED)
        }
    }

    private fun splitTextIntoChunks(text: String, maxLength: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        var lastSplitPos = 0

        var i = 0
        while (i < text.length) {
            val remainingLength = text.length - lastSplitPos

            if (remainingLength <= maxLength) {
                chunks.add(text.substring(lastSplitPos))
                break
            }

            val isSentenceEnd = checkSentenceEnd(text, i)
            val isMidPause = checkMidPause(text, i)

            if (isSentenceEnd || isMidPause) {
                val chunkLength = i - lastSplitPos + 1
                if (chunkLength <= maxLength) {
                    chunks.add(text.substring(lastSplitPos, i + 1))
                    lastSplitPos = i + 1
                    i++
                    continue
                }
            }

            val splitPos = findBestSplitPos(text, lastSplitPos, maxLength)
            if (splitPos > lastSplitPos) {
                chunks.add(text.substring(lastSplitPos, splitPos))
                lastSplitPos = splitPos
            } else {
                chunks.add(text.substring(lastSplitPos, lastSplitPos + maxLength))
                lastSplitPos += maxLength
            }
            i = lastSplitPos
        }

        return chunks
    }

    private fun checkSentenceEnd(text: String, index: Int): Boolean {
        if (index < 0) return false
        val sentenceEnds = listOf("。", "！", "？", ".", "!", "?")
        for (ender in sentenceEnds) {
            if (text.regionMatches(index, ender, 0, ender.length)) {
                return true
            }
        }
        return false
    }

    private fun checkMidPause(text: String, index: Int): Boolean {
        if (index < 0) return false
        val midPauses = listOf("，", "、", ",", ";", "；", "：", ":")
        for (pause in midPauses) {
            if (text.regionMatches(index, pause, 0, pause.length)) {
                return true
            }
        }
        return false
    }

    private fun findBestSplitPos(text: String, startPos: Int, maxLength: Int): Int {
        val searchEnd = minOf(startPos + maxLength, text.length)

        for (i in searchEnd - 1 downTo startPos + 1) {
            if (checkMidPause(text, i)) {
                return i + 1
            }
        }

        for (i in searchEnd - 1 downTo startPos + 1) {
            val char = text[i]
            if (char == ' ' || char == '\n' || char == '\t') {
                return i + 1
            }
        }

        return searchEnd
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
        val defaultVoice = voiceIds.firstOrNull() ?: "mimo_default"
        if (currentVoiceId != null && currentVoiceId.isNotBlank()) {
            return "$currentVoiceId$VOICE_NAME_SEPARATOR$lang"
        }
        return "$defaultVoice$VOICE_NAME_SEPARATOR$lang"
    }

    override fun isVoiceIdCorrect(voiceId: String?): Boolean {
        if (voiceId == null) return false
        val realVoiceName = extractRealVoiceName(voiceId)
        return realVoiceName != null && voiceIds.contains(realVoiceName)
    }

    private fun extractRealVoiceName(androidVoiceName: String?): String? {
        if (androidVoiceName == null) return null
        return if (androidVoiceName.contains(VOICE_NAME_SEPARATOR)) {
            androidVoiceName.substringBefore(VOICE_NAME_SEPARATOR)
        } else {
            androidVoiceName
        }
    }

    override fun stop() {
        logInfo("Stopping synthesis")
        isCancelled = true
        currentCall?.cancel()
        currentCall = null
        currentChannel?.close()
        currentChannel = null
        hasCompleted = false
        isFirstChunk = true
    }

    override fun release() {
        logInfo("Releasing engine")
        isCancelled = true
        currentCall?.cancel()
        currentCall = null
        currentChannel?.close()
        currentChannel = null
        cachedCloneAudioBase64 = null
        cachedCloneAudioPath = null
        engineScope.cancel()
        super.release()
    }

    override fun isConfigured(config: BaseEngineConfig?): Boolean {
        val mimoConfig = config as? MiMoTokenPlanConfig
        val result = mimoConfig?.apiKey?.isNotBlank() == true
        TtsLogger.d("$tag: isConfigured = $result")
        return result
    }

    override fun createDefaultConfig(): BaseEngineConfig {
        return MiMoTokenPlanConfig()
    }

    override fun getConfigLabel(configKey: String, context: android.content.Context): String? {
        return when (configKey) {
            "api_key" -> context.getString(R.string.api_key_label)
            "voice_id" -> context.getString(R.string.voice_select_label)
            "model" -> context.getString(R.string.model_select_label)
            "style_tag" -> context.getString(R.string.style_tag_label)
            "dialect" -> context.getString(R.string.dialect_label)
            "voice_design_description" -> context.getString(R.string.voice_design_description_label)
            "voice_clone_audio" -> context.getString(R.string.voice_clone_audio_label)
            "user_message" -> context.getString(R.string.user_message_label)
            "pre_generate_count" -> context.getString(R.string.pre_generate_count_label)
            else -> null
        }
    }
}
