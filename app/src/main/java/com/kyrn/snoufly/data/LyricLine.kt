package com.kyrn.snoufly.data

data class LyricLine(
    val timeStamp: Long,
    val content: String
)

object LrcParser {
    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        
        val lyrics = mutableListOf<LyricLine>()
        // Regex mejorada para soportar mm:ss.xx y mm:ss.xxx
        val tagRegex = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})]")
        
        lrcContent.lines().forEach { line ->
            val tags = tagRegex.findAll(line).toList()
            if (tags.isNotEmpty()) {
                val content = line.substring(tags.last().range.last + 1).trim()
                if (content.isNotEmpty()) {
                    tags.forEach { match ->
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toLong()
                        val msStr = match.groupValues[3]
                        val ms = when (msStr.length) {
                            2 -> msStr.toLong() * 10
                            3 -> msStr.toLong()
                            else -> 0L
                        }
                        val timeInMs = (min * 60 * 1000) + (sec * 1000) + ms
                        lyrics.add(LyricLine(timeInMs, content))
                    }
                }
            } else if (line.trim().isNotEmpty() && !line.startsWith("[")) {
                // Soporte para letras planas (sin tags de tiempo)
                lyrics.add(LyricLine(0L, line.trim()))
            }
        }
        
        return if (lyrics.isEmpty() && lrcContent.isNotBlank()) {
            // Fallback: tratar todo como texto plano si no hay tags
            lrcContent.lines()
                .filter { it.isNotBlank() }
                .map { LyricLine(0L, it.trim()) }
        } else {
            lyrics.sortedBy { it.timeStamp }
        }
    }
}
