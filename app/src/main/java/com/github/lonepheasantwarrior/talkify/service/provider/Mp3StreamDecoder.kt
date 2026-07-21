package com.github.lonepheasantwarrior.talkify.service.provider

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MP3 音频流解码工具。
 *
 * 提供 PCM 样本转换能力，供 AzureProvider 和 MiniMaxProvider 共用。
 * 各 Provider 的 MP3 解码循环因音频回调参数差异（声道数、采样率等）而有所不同，
 * 因此解码循环本身保留在各 Provider 中实现，此处仅抽取完全一致的 [shortArrayToByteArray]。
 */
object Mp3StreamDecoder {

    /**
     * 高性能 PCM 转换：利用 NIO ByteBuffer 直接内存块复制。
     *
     * 将 JLayer 解码后的 ShortArray 样本转换为小端序 16-bit PCM 字节数组。
     *
     * @param shortArray PCM 样本数组
     * @param length 有效样本数量
     * @return 小端序的 16-bit PCM 字节数组
     */
    fun shortArrayToByteArray(shortArray: ShortArray, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(shortArray, 0, length)
        return buffer.array()
    }
}
