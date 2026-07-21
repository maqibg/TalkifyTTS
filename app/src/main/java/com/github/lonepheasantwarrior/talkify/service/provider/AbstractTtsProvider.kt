package com.github.lonepheasantwarrior.talkify.service.provider

import android.content.Context
import androidx.annotation.XmlRes
import com.github.lonepheasantwarrior.talkify.R
import com.github.lonepheasantwarrior.talkify.TalkifyAppHolder
import com.github.lonepheasantwarrior.talkify.infrastructure.xml.VoiceXmlParser
import com.github.lonepheasantwarrior.talkify.service.TtsLogger

abstract class AbstractTtsProvider : TtsProviderApi {

    protected var isReleased: Boolean = false
        private set

    protected open val tag: String
        get() = javaClass.simpleName

    override fun stop() {
        TtsLogger.d("$tag: stop called")
    }

    override fun release() {
        TtsLogger.i("$tag: release called")
        isReleased = true
    }

    override fun getAudioConfig(): AudioConfig {
        return AudioConfig()
    }

    /**
     * 默认 API 地址，返回空字符串表示供应商不支持自定义 API 地址。
     * 子类可按需重写。
     */
    override fun getDefaultApiUrl(): String = ""

    /**
     * 默认模型 ID，返回空字符串表示供应商不支持自定义模型 ID。
     * 子类可按需重写。
     */
    override fun getDefaultModelId(): String = ""

    /**
     * 通用配置项标签的默认实现。
     *
     * 提供 api_url、model_id、voice_id 三个跨供应商通用标签。
     * 子类重写 [getConfigLabel] 时应将不匹配的 key 委托给 super。
     */
    override fun getConfigLabel(configKey: String, context: Context): String? {
        return when (configKey) {
            "api_url" -> context.getString(R.string.api_url_label)
            "model_id" -> context.getString(R.string.model_id_label)
            "voice_id" -> context.getString(R.string.voice_select_label)
            else -> null
        }
    }

    protected fun checkNotReleased() {
        if (isReleased) {
            val message = "Provider has been released"
            TtsLogger.e("$tag: $message")
            throw IllegalStateException(message)
        }
    }

    protected fun logDebug(message: String) {
        TtsLogger.d("$tag: $message")
    }

    protected fun logInfo(message: String) {
        TtsLogger.i("$tag: $message")
    }

    protected fun logWarning(message: String) {
        TtsLogger.w("$tag: $message")
    }

    protected fun logError(message: String, throwable: Throwable? = null) {
        TtsLogger.e("$tag: $message", throwable)
    }

    /**
     * 检查文本是否包含可朗读的文字内容
     * @return true 如果文本中至少包含一个文字字符（任意语言）
     */
    protected fun containsReadableText(text: String): Boolean {
        return text.any { Character.isLetter(it.code) }
    }

    // ==================== 公共工具方法 ====================

    companion object {
        /** 音色名称分隔符：分隔真实音色名与显示名 */
        private const val VOICE_NAME_SEPARATOR = "::"
    }

    /**
     * 从 Android 本地音色名称中提取真实的音色标识符。
     *
     * 格式："<真实音色名>::<显示名称>"，提取 `::` 之前的部分。
     * 若不包含分隔符，则返回原始名称。
     */
    protected fun extractRealVoiceName(androidVoiceName: String?): String? {
        if (androidVoiceName == null) return null
        return if (androidVoiceName.contains(VOICE_NAME_SEPARATOR)) {
            androidVoiceName.substringBefore(VOICE_NAME_SEPARATOR)
        } else {
            androidVoiceName
        }
    }

    /**
     * 从 XML 资源加载音色 ID 列表。
     *
     * 使用 [VoiceXmlParser] 解析语音定义的 XML 资源文件，
     * 提取其中的音色标识符列表。
     *
     * @param xmlResId XML 资源 ID（如 R.xml.minimax_voices）
     * @return 音色 ID 列表，解析失败或 Context 不可用时返回空列表
     */
    protected fun loadVoiceIdsFromXml(@XmlRes xmlResId: Int): List<String> {
        val context = TalkifyAppHolder.getContext()
        return if (context != null) {
            try {
                VoiceXmlParser.parseVoiceIds(context, xmlResId)
            } catch (e: Exception) {
                TtsLogger.e("$tag: Failed to load voice IDs from resource", throwable = e)
                emptyList()
            }
        } else {
            TtsLogger.w("$tag: Context not available, voice IDs will be empty")
            emptyList()
        }
    }
}
