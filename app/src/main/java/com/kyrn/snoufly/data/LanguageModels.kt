package com.kyrn.snoufly.data

import androidx.annotation.Keep

@Keep
data class LanguageInfo(
    val code: String,
    val name: String,
    val nativeName: String,
    val version: String,
    val minAppVersion: String,
    val author: String,
    val contributors: List<String>,
    val file: String,
    val size: Long,
    val updatedAt: String,
    val checksum: String,
    val rtl: Boolean
)

@Keep
data class TranslationMap(
    val common: Map<String, Any> = emptyMap(),
    val library: Map<String, Any> = emptyMap(),
    val player: Map<String, Any> = emptyMap(),
    val favorites: Map<String, Any> = emptyMap(),
    val equalizer: Map<String, Any> = emptyMap(),
    val settings: Map<String, Any> = emptyMap(),
    val edit_dialog: Map<String, Any> = emptyMap(),
    val song_item: Map<String, Any> = emptyMap()
)
