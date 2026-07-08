package com.vx.browser.browser

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object MediaProbe {

    enum class MediaType { VIDEO, AUDIO, M3U8, MPD, OTHER }

    data class MediaMeta(
        val size: Long = 0,
        val contentType: String = "",
        val ext: String = "",
        val width: Int = 0,
        val height: Int = 0
    )

    private val metaCache = ConcurrentHashMap<String, MediaMeta>()

    fun probe(url: String): MediaType {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            val ct = (conn.contentType ?: "").lowercase()
            val type = when {
                ct.contains("mpegurl") || ct.contains("x-m3u8") -> MediaType.M3U8
                ct.contains("dash+xml") || ct.contains("dash") -> MediaType.MPD
                ct.contains("video") -> MediaType.VIDEO
                ct.contains("audio") -> MediaType.AUDIO
                ct.contains("octet-stream") -> MediaType.VIDEO
                else -> MediaType.OTHER
            }
            conn.disconnect()
            type
        } catch (e: Exception) {
            MediaType.OTHER
        }
    }

    fun fetchAndParseM3u8(baseUrl: String): List<String> {
        return try {
            val conn = URL(baseUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(baseUrl) ?: "")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseM3u8(baseUrl, text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 取链接元数据：大小/类型/扩展名/（视频）尺寸，带缓存 */
    fun fetchMeta(url: String): MediaMeta {
        metaCache[url]?.let { return it }
        val m = runCatching { doFetchMeta(url) }.getOrNull() ?: MediaMeta()
        if (m.size > 0 || m.contentType.isNotEmpty()) metaCache[url] = m
        return m
    }

    private fun doFetchMeta(url: String): MediaMeta {
        val cookie = CookieManager.getInstance().getCookie(url) ?: ""
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Cookie", cookie)
        var len = conn.contentLengthLong
        var ct = (conn.contentType ?: "").lowercase()
        if (len <= 0) {
            conn.disconnect()
            val c2 = URL(url).openConnection() as HttpURLConnection
            c2.requestMethod = "GET"
            c2.setRequestProperty("Range", "bytes=0-0")
            c2.setRequestProperty("Cookie", cookie)
            c2.connectTimeout = 6000
            c2.readTimeout = 6000
            val cr = c2.getHeaderField("Content-Range")
            len = parseContentRangeTotal(cr)
            if (ct.isBlank()) ct = (c2.contentType ?: "").lowercase()
            c2.disconnect()
        } else {
            conn.disconnect()
        }
        val ext = extOf(url, ct)
        var w = 0
        var h = 0
        val isVideo = ct.contains("video") || isVideoExt(ext)
        if (isVideo && (len <= 0 || len > 1024)) {
            // 小范围 GET 解析 MP4 尺寸（best-effort）
            try {
                val c3 = URL(url).openConnection() as HttpURLConnection
                c3.requestMethod = "GET"
                c3.setRequestProperty("Range", "bytes=0-262143")
                c3.setRequestProperty("Cookie", cookie)
                c3.connectTimeout = 8000
                c3.readTimeout = 8000
                val buf = c3.inputStream.use { it.readBytes() }
                c3.disconnect()
                val wh = parseMp4Dims(buf)
                w = wh.first; h = wh.second
            } catch (e: Exception) {
            }
        }
        return MediaMeta(len, ct, ext, w, h)
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "未知大小"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    private fun parseContentRangeTotal(cr: String?): Long {
        if (cr == null) return 0
        val slash = cr.lastIndexOf('/')
        if (slash >= 0) return runCatching { cr.substring(slash + 1).toLong() }.getOrNull() ?: 0
        return 0
    }

    private fun extOf(url: String, ct: String): String {
        val lower = url.lowercase()
        val q = lower.indexOf('?')
        val path = if (q >= 0) lower.substring(0, q) else lower
        val dot = path.lastIndexOf('.')
        val slash = path.lastIndexOf('/')
        if (dot > slash) {
            val e = path.substring(dot + 1)
            if (e.length in 2..5 && e.all { it.isLetterOrDigit() }) return e
        }
        return ctTypeExt(ct)
    }

    private fun ctTypeExt(ct: String): String {
        return when {
            ct.contains("mpegurl") || ct.contains("x-m3u8") -> "m3u8"
            ct.contains("dash") -> "mpd"
            ct.contains("mp4") -> "mp4"
            ct.contains("webm") -> "webm"
            ct.contains("x-matroska") -> "mkv"
            ct.contains("mpeg") -> "mpeg"
            ct.contains("ogg") -> "ogg"
            ct.contains("mp3") -> "mp3"
            ct.contains("wav") -> "wav"
            ct.contains("aac") -> "aac"
            ct.contains("flv") -> "flv"
            ct.contains("video") -> "video"
            ct.contains("audio") -> "audio"
            else -> ""
        }
    }

    private fun isVideoExt(ext: String): Boolean =
        ext in setOf("mp4", "webm", "mkv", "mov", "flv", "m4v", "avi", "mpeg", "mpg")

    /** best-effort 解析 MP4 tkhd 中的宽高（16.16 定点） */
    private fun parseMp4Dims(buf: ByteArray): Pair<Int, Int> {
        try {
            var i = 0
            while (i + 8 <= buf.size) {
                val size = readInt(buf, i)
                if (size <= 0 || i + size > buf.size + 8) break
                val type = String(buf, i + 4, 4, Charsets.US_ASCII)
                if (type == "moov") {
                    var j = i + 8
                    val end = i + size
                    while (j + 8 <= end && j + 8 <= buf.size) {
                        val s2 = readInt(buf, j)
                        if (s2 <= 0) break
                        val t2 = String(buf, j + 4, 4, Charsets.US_ASCII)
                        if (t2 == "tkhd") {
                            val version = buf[j + 8].toInt() and 0xff
                            val off = if (version == 1) 96 else 84
                            if (j + off + 8 <= buf.size) {
                                val w = readInt(buf, j + off) ushr 16
                                val h = readInt(buf, j + off + 4) ushr 16
                                if (w > 0 && h > 0) return Pair(w, h)
                            }
                            break
                        }
                        j += s2
                    }
                    break
                }
                i += size
            }
        } catch (e: Exception) {
        }
        return Pair(0, 0)
    }

    private fun readInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xff) shl 24) or
                ((b[off + 1].toInt() and 0xff) shl 16) or
                ((b[off + 2].toInt() and 0xff) shl 8) or
                (b[off + 3].toInt() and 0xff)

    private fun parseM3u8(base: String, text: String): List<String> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val out = mutableListOf<String>()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val j = i + 1
                if (j < lines.size && !lines[j].startsWith("#")) out.add(resolve(base, lines[j]))
            } else if (!line.startsWith("#") &&
                (line.endsWith(".ts") || line.endsWith(".m4s") || line.endsWith(".aac") || line.endsWith(".mp4"))
            ) {
                out.add(resolve(base, line))
            }
        }
        return out.distinct()
    }

    private fun resolve(base: String, uri: String): String {
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        return try {
            URL(URL(base), uri).toString()
        } catch (e: Exception) {
            uri
        }
    }
}
