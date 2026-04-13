package com.kyrn.snoufly.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

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
    val common: Map<String, String> = emptyMap(),
    val library: Map<String, String> = emptyMap(),
    val player: Map<String, String> = emptyMap(),
    val favorites: Map<String, String> = emptyMap(),
    val equalizer: Map<String, String> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
    val edit_dialog: Map<String, String> = emptyMap(),
    val song_item: Map<String, String> = emptyMap()
)
