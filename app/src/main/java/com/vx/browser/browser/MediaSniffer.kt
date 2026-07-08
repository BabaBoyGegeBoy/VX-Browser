package com.vx.browser.browser

import java.util.LinkedHashSet

object MediaSniffer {
    private val mediaExt = setOf(
        "mp4", "m3u8", "m3u", "mp3", "webm", "ogg", "wav", "ts", "flv",
        "mov", "m4a", "aac", "opus", "mkv", "avi", "mpg", "mpeg", "3gp"
    )
    private val urls = LinkedHashSet<String>()
    var enabled = true
    private const val MAX = 300

    fun maybeMedia(url: String): Boolean {
        if (!enabled) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        val q = lower.indexOf('?')
        val path = if (q >= 0) lower.substring(0, q) else lower
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        if (dot <= slash) return false
        return mediaExt.contains(path.substring(dot + 1))
    }

    fun isCandidate(url: String): Boolean {
        if (!enabled) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        val q = lower.indexOf('?')
        val path = if (q >= 0) lower.substring(0, q) else lower
        val hasExt = path.contains('.')
        if (!hasExt) return true
        return lower.contains("video") || lower.contains("audio") || lower.contains("play")
            || lower.contains("media") || lower.contains("m3u8") || lower.contains("mpd")
    }

    fun add(url: String) {
        if (urls.size >= MAX) urls.remove(urls.first())
        urls.add(url)
    }

    fun addAll(list: List<String>) {
        list.forEach { add(it) }
    }

    fun list(): List<String> = urls.toList()
    fun clear() = urls.clear()
}
