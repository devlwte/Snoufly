package com.kyrn.snoufly.data

import android.net.Uri
import androidx.annotation.Keep

@Keep
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long,
    val path: String? = null
)
