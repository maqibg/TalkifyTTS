package com.github.lonepheasantwarrior.talkify.service.engine

/**
 * 文本分块工具类
 *
 * 将长文本按语义边界（句号、逗号等）分割为不超过 maxLength 的块，
 * 用于各 TTS 引擎的流式合成。
 */
object TextChunker {

    private val SENTENCE_ENDS = listOf("。", "！", "？", ".", "!", "?")
    private val MID_PAUSES = listOf("，", "、", ",", ";", "；", "：", ":")

    fun splitTextIntoChunks(text: String, maxLength: Int): List<String> {
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
        for (ender in SENTENCE_ENDS) {
            if (text.regionMatches(index, ender, 0, ender.length)) {
                return true
            }
        }
        return false
    }

    private fun checkMidPause(text: String, index: Int): Boolean {
        if (index < 0) return false
        for (pause in MID_PAUSES) {
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
}
