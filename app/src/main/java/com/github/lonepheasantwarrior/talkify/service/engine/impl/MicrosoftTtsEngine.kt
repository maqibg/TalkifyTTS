package com.github.lonepheasantwarrior.talkify.service.engine.impl

import android.content.Context
import android.speech.tts.Voice
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.domain.model.BaseEngineConfig
import com.github.lonepheasantwarrior.talkify.domain.model.MicrosoftTtsConfig
import com.github.lonepheasantwarrior.talkify.service.TtsErrorCode
import com.github.lonepheasantwarrior.talkify.service.TtsLogger
import com.github.lonepheasantwarrior.talkify.service.engine.AbstractTtsEngine
import com.github.lonepheasantwarrior.talkify.service.engine.AudioConfig
import com.github.lonepheasantwarrior.talkify.service.engine.SynthesisParams
import com.github.lonepheasantwarrior.talkify.service.engine.TtsSynthesisListener
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * 微软语音合成引擎实现
 *
 * 继承 [AbstractTtsEngine]，实现 TTS 引擎接口
 * 支持真正的流式音频合成，边接收边播放
 * 优化点：在单次合成任务中复用单条 WebSocket 连接，消除多段落情况下的重复握手延迟
 * 服务提供商：Azure
 */
class MicrosoftTtsEngine : AbstractTtsEngine() {

    companion object {
        const val ENGINE_ID = "microsoft-tts"
        const val ENGINE_NAME = "微软语音合成"

        private const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val WSS_URL = "wss://$BASE_URL/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
        private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
        private const val CHROMIUM_MAJOR_VERSION = "143"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"

        private const val WIN_EPOCH = 11644473600L
        private const val S_TO_NS = 1_000_000_000L

        private const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
        private const val MAX_TEXT_LENGTH = 4096
        private const val PIPE_BUFFER_SIZE = 65536 // 64KB 扩容管道，防止 OkHttp 接收线程阻塞拖慢网络

        private val SUPPORTED_LANGUAGES = arrayOf("zho", "eng", "deu", "ita", "por", "spa", "jpn", "kor", "fra", "rus")
        private val random = Random()

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.US_ASCII))
            return hash.joinToString("") { "%02x".format(it).uppercase(Locale.US) }
        }

        private fun generateSecMsGec(): String {
            val currentTimeSeconds = System.currentTimeMillis() / 1000.0
            var ticks = currentTimeSeconds + WIN_EPOCH
            ticks -= ticks % 300
            ticks *= S_TO_NS / 100.0
            val strToHash = "${ticks.toLong()}$TRUSTED_CLIENT_TOKEN"
            return sha256Hex(strToHash)
        }

        private fun generateMuid(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it).uppercase(Locale.US) }
        }

        private fun getHeadersWithMuid(): Map<String, String> {
            return mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 " +
                        "Edg/$CHROMIUM_MAJOR_VERSION.0.0.0",
                "Accept-Encoding" to "gzip, deflate, br, zstd",
                "Accept-Language" to "en-US,en;q=0.9",
                "Pragma" to "no-cache",
                "Cache-Control" to "no-cache",
                "Origin" to "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
                "Sec-WebSocket-Version" to "13",
                "Cookie" to "muid=${generateMuid()};"
            )
        }

        private fun dateToString(): String {
            val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }

        private fun connectId(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }

        private fun mkssml(voice: String, rate: String, volume: String, pitch: String, text: String): String {
            return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                    "<voice name='$voice'>" +
                    "<prosody pitch='$pitch' rate='$rate' volume='$volume'>" +
                    escapeXml(text) +
                    "</prosody>" +
                    "</voice>" +
                    "</speak>"
        }

        private fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        private fun ssmlHeadersPlusData(requestId: String, timestamp: String, ssml: String): String {
            return "X-RequestId:$requestId\r\n" +
                    "Content-Type:application/ssml+xml\r\n" +
                    "X-Timestamp:${timestamp}Z\r\n" +
                    "Path:ssml\r\n\r\n" +
                    ssml
        }

        private fun removeIncompatibleCharacters(text: String): String {
            val chars = text.toCharArray()
            for (i in chars.indices) {
                val code = chars[i].code
                if ((code in 0..8) || (code in 11..12) || (code in 14..31)) {
                    chars[i] = ' '
                }
            }
            return String(chars)
        }

        private fun splitTextByByteLength(text: String): List<String> {
            val chunks = mutableListOf<String>()
            val utf8Bytes = text.toByteArray(Charsets.UTF_8)
            var offset = 0
            while (offset < utf8Bytes.size) {
                var end = min(offset + MAX_TEXT_LENGTH, utf8Bytes.size)
                end = findSafeUtf8SplitPoint(utf8Bytes, end)
                end = findBestSplitPoint(utf8Bytes, offset, end)
                if (end <= offset) {
                    end = min(offset + MAX_TEXT_LENGTH, utf8Bytes.size)
                    end = findSafeUtf8SplitPoint(utf8Bytes, end)
                }
                val chunk = String(utf8Bytes, offset, end - offset, Charsets.UTF_8).trim()
                if (chunk.isNotEmpty()) {
                    chunks.add(chunk)
                }
                offset = end
            }
            return chunks
        }

        private fun findSafeUtf8SplitPoint(bytes: ByteArray, end: Int): Int {
            var splitAt = end
            while (splitAt > 0) {
                try {
                    String(bytes, 0, splitAt, Charsets.UTF_8)
                    return splitAt
                } catch (_: Exception) {
                    splitAt--
                }
            }
            return splitAt
        }

        private fun findBestSplitPoint(bytes: ByteArray, start: Int, end: Int): Int {
            val subBytes = if (end <= bytes.size) bytes.copyOfRange(0, end) else bytes
            var splitAt = subBytes.lastIndexOf('\n'.code.toByte())
            if (splitAt >= start) {
                return splitAt + 1
            }
            splitAt = subBytes.lastIndexOf(' '.code.toByte())
            if (splitAt >= start) {
                return splitAt + 1
            }
            return end
        }
    }

    private var currentWebSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var isCancelled = false
    private var hasCompleted = false

    private val engineJob = SupervisorJob()
    private val engineScope = CoroutineScope(Dispatchers.IO + engineJob)

    private var synthesisJob: Job? = null

    init {
        // DNS 预热：前置网络层握手准备，显著降低首次合成请求时的 DNS 解析延迟
        engineScope.launch {
            try {
                java.net.InetAddress.getByName("speech.platform.bing.com")
            } catch (_: Exception) {
                // 忽略预热异常，不影响主流程
            }
        }
    }

    override fun getEngineId(): String = ENGINE_ID
    override fun getEngineName(): String = ENGINE_NAME

    override fun synthesize(
        text: String, params: SynthesisParams, config: BaseEngineConfig, listener: TtsSynthesisListener
    ) {
        checkNotReleased()

        val msConfig = config as? MicrosoftTtsConfig
        if (msConfig == null) {
            logError("Invalid config type, expected MicrosoftTtsConfig")
            listener.onError(TtsErrorCode.getErrorMessage(TtsErrorCode.ERROR_ENGINE_NOT_CONFIGURED))
            return
        }

        val cleanedText = removeIncompatibleCharacters(text)
        val textChunks = splitTextByByteLength(cleanedText)

        if (textChunks.isEmpty()) {
            logWarning("待朗读文本内容为空")
            listener.onSynthesisCompleted()
            return
        }

        logInfo("Starting Microsoft TTS synthesis: textLength=${text.length}, chunks=${textChunks.size}")

        isCancelled = false
        hasCompleted = false

        synthesisJob = engineScope.launch {
            try {
                listener.onSynthesisStarted()
                processChunks(textChunks, params, msConfig, listener)
                if (!isCancelled) {
                    listener.onSynthesisCompleted()
                }
            } catch (e: Exception) {
                if (!isCancelled && e !is CancellationException) {
                    logError("Synthesis error", e)
                    listener.onError("合成失败：${e.message}")
                }
            }
        }
    }

    private suspend fun processChunks(
        chunks: List<String>,
        params: SynthesisParams,
        config: MicrosoftTtsConfig,
        listener: TtsSynthesisListener
    ) {
        val pipeClosed = AtomicBoolean(false)
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = withContext(Dispatchers.IO) {
            PipedInputStream(pipedOutputStream, PIPE_BUFFER_SIZE)
        }

        // 解码是 CPU 密集型操作，调度至 Default
        // 现在的 DecodeJob 贯穿整个 synthesis 周期，不用为每个 chunk 反复创建
        val decodeJob = engineScope.launch(Dispatchers.Default) {
            decodeMp3Stream(pipedInputStream, listener)
        }

        var webSocket: WebSocket? = null

        try {
            // 1. 建立全局 WebSocket 连接
            val wsListener = MicrosoftWebSocketListener(pipedOutputStream, pipeClosed)
            webSocket = connectWebSocket(wsListener)
            currentWebSocket = webSocket

            val voice = config.voiceId.ifEmpty { DEFAULT_VOICE }
            val rate = convertRate(params.speechRate)
            val volume = convertVolume(params.volume)
            val pitch = convertPitch(params.pitch)

            // 2. 顺序处理 Chunks
            for ((index, chunk) in chunks.withIndex()) {
                if (isCancelled) break
                logDebug("Processing chunk ${index + 1}/${chunks.size}")

                val chunkDeferred = CompletableDeferred<Result<Unit>>()
                wsListener.setCurrentChunkDeferred(chunkDeferred)

                sendSsmlMessage(webSocket, voice, rate, volume, pitch, chunk)

                // 等待当前 chunk 返回 Path:turn.end
                val result = chunkDeferred.await()
                result.getOrThrow()
            }
        } finally {
            // 3. 统一资源清理
            try {
                withContext(Dispatchers.IO) {
                    pipedOutputStream.close()
                }
            } catch (_: Exception) {}
            pipeClosed.set(true)
            webSocket?.close(1000, "Done")
            currentWebSocket = null

            // 等待音频播放线程安全退出
            decodeJob.join()
        }
    }

    private suspend fun connectWebSocket(listener: MicrosoftWebSocketListener): WebSocket {
        val connectionId = connectId()
        val url = "$WSS_URL&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=${generateSecMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val requestBuilder = Request.Builder().url(url)
        getHeadersWithMuid().forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        client.newWebSocket(requestBuilder.build(), listener)
        return listener.awaitConnection()
    }

    /**
     * 内部 WebSocket 监听器，负责接管长连接的状态并分发音频流和 Chunk 结束信号
     */
    inner class MicrosoftWebSocketListener(
        private val pipedOutputStream: PipedOutputStream,
        private val pipeClosed: AtomicBoolean
    ) : WebSocketListener() {

        private val connectionDeferred = CompletableDeferred<Result<WebSocket>>()
        private var currentChunkDeferred: CompletableDeferred<Result<Unit>>? = null

        fun setCurrentChunkDeferred(deferred: CompletableDeferred<Result<Unit>>) {
            currentChunkDeferred = deferred
        }

        suspend fun awaitConnection(): WebSocket {
            return connectionDeferred.await().getOrThrow()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            logDebug("WebSocket connected")
            try {
                sendConfigMessage(webSocket)
                connectionDeferred.complete(Result.success(webSocket))
            } catch (e: Exception) {
                connectionDeferred.complete(Result.failure(e))
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (pipeClosed.get() || isCancelled) return
            try {
                val buffer = bytes.asByteBuffer()
                if (buffer.remaining() >= 2) {
                    val headerLength = (buffer.get().toInt() and 0xFF) shl 8 or (buffer.get().toInt() and 0xFF)
                    if (buffer.remaining() >= headerLength) {
                        val headerBytes = ByteArray(headerLength)
                        buffer.get(headerBytes)
                        val headerStr = String(headerBytes, Charsets.UTF_8)

                        if (headerStr.contains("Path:audio") || headerStr.contains("Path: audio")) {
                            if (buffer.remaining() > 0) {
                                val audioData = ByteArray(buffer.remaining())
                                buffer.get(audioData)
                                pipedOutputStream.write(audioData)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                handleFailure(webSocket, e)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (pipeClosed.get() || isCancelled) return
            try {
                if (text.contains("Path:turn.end") || text.contains("Path: turn.end")) {
                    // 通知当前 Chunk 结束，可以继续发送下一个 Chunk 了
                    currentChunkDeferred?.complete(Result.success(Unit))
                }
            } catch (e: Exception) {
                logError("Error processing text message", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            handleCloseOrFailure(code, reason, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleCloseOrFailure(code, reason, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleCloseOrFailure(null, null, t)
        }

        private fun handleFailure(webSocket: WebSocket, e: Exception) {
            if (!pipeClosed.get()) {
                logError("WebSocket error processing message", e)
                webSocket.close(1000, "Error")
                handleCloseOrFailure(null, null, e)
            }
        }

        private fun handleCloseOrFailure(code: Int?, reason: String?, t: Throwable?) {
            pipeClosed.set(true)
            try { pipedOutputStream.close() } catch (_: Exception) {}

            val exception = t ?: Exception("WebSocket closed with code: $code, reason: $reason")

            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(Result.failure(exception))
            }

            currentChunkDeferred?.let { deferred ->
                if (!deferred.isCompleted) {
                    // 如果正常 1000 关闭，且最后一段还没闭合，当做成功处理；否则上抛异常
                    if (code == 1000) deferred.complete(Result.success(Unit))
                    else deferred.complete(Result.failure(exception))
                }
            }
        }
    }

    private fun decodeMp3Stream(inputStream: PipedInputStream, listener: TtsSynthesisListener) {
        val bitstream = Bitstream(inputStream)
        val decoder = Decoder()
        var sampleRate: Int

        try {
            while (!isCancelled) {
                val header: Header = bitstream.readFrame() ?: break

                sampleRate = header.frequency()

                val sampleBuffer = decoder.decodeFrame(header, bitstream) as SampleBuffer
                val samples = sampleBuffer.buffer
                val sampleCount = sampleBuffer.bufferLength

                if (sampleCount > 0) {
                    val pcmBytes = shortArrayToByteArray(samples, sampleCount)
                    listener.onAudioAvailable(
                        pcmBytes,
                        sampleRate,
                        AudioConfig.DEFAULT_AUDIO_FORMAT,
                        AudioConfig.DEFAULT_CHANNEL_COUNT
                    )
                }

                bitstream.closeFrame()
            }
        } catch (e: Exception) {
            // 当我们主动关闭 PipedOutputStream 时，Bitstream 可能会抛出一些可预见的流结束异常，只需记录 Debug 即可
            logDebug("MP3 decoding finished or interrupted: ${e.message}")
        } finally {
            try { bitstream.close() } catch (_: Exception) {}
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    /**
     * 高性能 PCM 转换方案：利用 NIO ByteBuffer 内存块直接复制机制，大幅度减少循环的 CPU 和时间开销
     */
    private fun shortArrayToByteArray(shortArray: ShortArray, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(shortArray, 0, length)
        return buffer.array()
    }

    private fun sendConfigMessage(webSocket: WebSocket) {
        val configMessage = "X-Timestamp:${dateToString()}\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
        webSocket.send(configMessage)
    }

    private fun sendSsmlMessage(
        webSocket: WebSocket,
        voice: String,
        rate: String,
        volume: String,
        pitch: String,
        text: String
    ) {
        val ssml = mkssml(voice, rate, volume, pitch, text)
        // 注意：每次发送 SSML 都需要重新生成一个新的 RequestId 以区分 Turn
        val message = ssmlHeadersPlusData(connectId(), dateToString(), ssml)
        webSocket.send(message)
    }

    private fun convertRate(speechRate: Float): String {
        val ratePercent = ((speechRate - 100) / 100 * 100).toInt()
        return if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
    }

    private fun convertVolume(volume: Float): String {
        val volumePercent = (volume * 100 - 100).toInt()
        return if (volumePercent >= 0) "+${volumePercent}%" else "${volumePercent}%"
    }

    private fun convertPitch(pitch: Float): String {
        val pitchHz = ((pitch - 100) / 100 * 50).toInt()
        return if (pitchHz >= 0) "+${pitchHz}Hz" else "${pitchHz}Hz"
    }

    override fun getSupportedLanguages(): Set<String> {
        return SUPPORTED_LANGUAGES.toSet()
    }

    override fun getDefaultLanguages(): Array<String> {
        return arrayOf(Locale.SIMPLIFIED_CHINESE.isO3Language, Locale.SIMPLIFIED_CHINESE.isO3Country, "")
    }

    override fun getSupportedVoices(): List<Voice> {
        val voices = mutableListOf<Voice>()
        for (langCode in getSupportedLanguages()) {
            val locale = Locale.forLanguageTag(langCode)
            voices.add(
                Voice(
                    DEFAULT_VOICE,
                    locale,
                    Voice.QUALITY_NORMAL,
                    Voice.LATENCY_NORMAL,
                    true,
                    emptySet()
                )
            )
        }
        return voices
    }

    override fun getDefaultVoiceId(lang: String?, country: String?, variant: String?, currentVoiceId: String?): String {
        return currentVoiceId ?: DEFAULT_VOICE
    }

    override fun isVoiceIdCorrect(voiceId: String?): Boolean {
        return !voiceId.isNullOrBlank()
    }

    override fun stop() {
        logInfo("Stopping synthesis")
        isCancelled = true
        currentWebSocket?.close(1000, "Stopped by user")
        currentWebSocket = null
        synthesisJob?.cancel()
        synthesisJob = null
    }

    override fun release() {
        logInfo("Releasing engine")
        isCancelled = true
        currentWebSocket?.close(1000, "Released")
        currentWebSocket = null
        synthesisJob?.cancel()
        synthesisJob = null
        engineJob.cancel()
        super.release()
    }

    override fun isConfigured(config: BaseEngineConfig?): Boolean {
        val result = config is MicrosoftTtsConfig
        TtsLogger.d("$tag: isConfigured = $result")
        return result
    }

    override fun createDefaultConfig(): BaseEngineConfig {
        return MicrosoftTtsConfig()
    }

    override fun getConfigLabel(configKey: String, context: Context): String? {
        return when (configKey) {
            "voice_id" -> context.getString(R.string.voice_select_label)
            else -> null
        }
    }
}