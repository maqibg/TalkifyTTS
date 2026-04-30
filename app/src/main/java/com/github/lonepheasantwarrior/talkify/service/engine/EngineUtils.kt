package com.github.lonepheasantwarrior.talkify.service.engine

/**
 * 将 Headers 转换为脱敏字符串用于日志
 *
 * 自动识别并脱敏常见的敏感 header: api-key, x-api-key, authorization 等
 */
fun okhttp3.Headers.toMaskedSensitive(): String {
    val sb = StringBuilder("{")
    for (i in 0 until this.size) {
        val name = this.name(i)
        val value = this.value(i)
        val maskedValue = when (name.lowercase()) {
            "api-key", "x-api-key" -> "${value.take(4)}****${value.takeLast(4)}"
            "authorization", "x-authorization" -> {
                if (value.startsWith("Bearer ", ignoreCase = true)) "Bearer ****"
                else "****"
            }
            else -> value
        }
        sb.append("$name=$maskedValue")
        if (i < this.size - 1) sb.append(", ")
    }
    sb.append("}")
    return sb.toString()
}
