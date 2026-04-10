package com.kyrn.snoufly.data

data class CustomMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverUri: String? = null,
    val lrcUri: String? = null
) {
    fun serialize(): String {
        return listOf(
            "t:${title ?: ""}",
            "a:${artist ?: ""}",
            "al:${album ?: ""}",
            "c:${coverUri ?: ""}",
            "l:${lrcUri ?: ""}"
        ).joinToString(";;")
    }

    companion object {
        fun deserialize(raw: String): CustomMetadata {
            val parts = raw.split(";;").associate { 
                val kv = it.split(":", limit = 2)
                if (kv.size == 2) kv[0] to kv[1] else "" to ""
            }
            return CustomMetadata(
                title = parts["t"]?.takeIf { it.isNotBlank() },
                artist = parts["a"]?.takeIf { it.isNotBlank() },
                album = parts["al"]?.takeIf { it.isNotBlank() },
                coverUri = parts["c"]?.takeIf { it.isNotBlank() },
                lrcUri = parts["l"]?.takeIf { it.isNotBlank() }
            )
        }
    }
}
