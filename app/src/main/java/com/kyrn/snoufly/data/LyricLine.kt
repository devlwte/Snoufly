package com.kyrn.snoufly.data

data class LyricLine(
    val timeStamp: Long,
    val content: String
)

object LrcParser {
    fun parse(lrcContent: String): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        
        lrcContent.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val content = match.groupValues[4].trim()
                
                val timeInMs = (min * 60 * 1000) + (sec * 1000) + ms
                lyrics.add(LyricLine(timeInMs, content))
            }
        }
        return lyrics.sortedBy { it.timeStamp }
    }
}
