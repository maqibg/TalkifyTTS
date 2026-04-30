package com.github.lonepheasantwarrior.talkify.domain.model

data class ConfigItem(
    val key: String,
    val label: String,
    val value: String,
    val isPassword: Boolean = false,
    val isVoiceSelector: Boolean = false,
    val isDropdown: Boolean = false,
    val dropdownOptions: List<Pair<String, String>> = emptyList(),
    val isTextArea: Boolean = false,
    val isFileSelector: Boolean = false
)
