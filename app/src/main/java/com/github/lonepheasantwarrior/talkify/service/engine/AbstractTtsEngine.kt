package com.github.lonepheasantwarrior.talkify.service.engine

import com.github.lonepheasantwarrior.talkify.service.TtsLogger

abstract class AbstractTtsEngine : TtsEngineApi {

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
     * @return true if engine is NOT released and ready to use
     */
    protected fun checkNotReleased(): Boolean {
        if (isReleased) {
            TtsLogger.e("$tag: Engine has been released, ignoring call")
            return false
        }
        return true
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
}
