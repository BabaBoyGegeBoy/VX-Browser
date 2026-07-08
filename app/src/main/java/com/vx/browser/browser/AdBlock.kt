package com.vx.browser.browser

import android.content.Context
import java.io.File

object AdBlock {
    // 域名规则：请求 host 等于 d 或以 ".$d" 结尾则拦截
    private val domainRules = HashSet<String>()
    // 关键字规则：url 中包含该子串则拦截（来自 /xxx/ 写法）
    private val patternRules = HashSet<String>()
    // 元素隐藏规则：(限定域名集合? , CSS 选择器)；domains 为 null 表示全局生效
    private val hideRules = ArrayList<Pair<Set<String>?, String>>()
    var enabled = true

    private const val USER_FILE = "adblock_user.txt"

    fun load(context: Context) {
        domainRules.clear()
        patternRules.clear()
        hideRules.clear()
        val builtin = runCatching {
            context.assets.open("adblock.txt").bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList())
        val userFile = File(context.filesDir, USER_FILE)
        val user = if (userFile.exists()) {
            runCatching { userFile.readLines() }.getOrDefault(emptyList())
        } else emptyList()
        parse(builtin + user)
    }

    /** 解析 AdGuard / EasyList 风格规则（仅支持基础子集） */
    fun parse(lines: List<String>) {
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#")) continue
            // 元素隐藏规则：example.com,example2.com##.sel 或 ##.sel
            if (line.contains("##")) {
                val parts = line.split("##", limit = 2)
                val domPart = parts[0].trim()
                val sel = parts[1].trim()
                if (sel.isEmpty()) continue
                val domains = if (domPart.isEmpty()) null
                else domPart.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                hideRules.add(domains to sel)
                continue
            }
            when {
                line.startsWith("||") -> {
                    var d = line.substring(2)
                    val caret = d.indexOf('^')
                    if (caret >= 0) d = d.substring(0, caret)
                    d = d.lowercase()
                    if (d.isNotEmpty()) domainRules.add(d)
                }
                line.startsWith("/") && line.endsWith("/") -> {
                    val p = line.substring(1, line.length - 1).lowercase()
                    if (p.isNotEmpty()) patternRules.add(p)
                }
                line.contains('.') -> domainRules.add(line.lowercase())
                else -> {
                    // 无点的纯品牌名（如 doubleclick / youtube）为保证不误杀整站，忽略
                }
            }
        }
    }

    fun isBlocked(url: String): Boolean {
        if (!enabled) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        val host = hostOf(lower)
        if (host != null) {
            for (d in domainRules) {
                if (host == d || host.endsWith(".$d")) return true
            }
        }
        for (p in patternRules) {
            if (lower.contains(p)) return true
        }
        return false
    }

    /** 返回针对该 host 的元素隐藏 CSS（含 display:none!important），无规则返回空串 */
    fun elementCssFor(host: String?): String {
        if (!enabled || host == null) return ""
        val sb = StringBuilder()
        for ((domains, sel) in hideRules) {
            if (domains == null || domains.any { host == it || host.endsWith(".$it") }) {
                sb.append(sel).append(',')
            }
        }
        if (sb.isEmpty()) return ""
        sb.deleteCharAt(sb.length - 1)
        return "${sb}{display:none!important}"
    }

    /** 追加导入用户规则并持久化，增量生效 */
    fun importRules(context: Context, lines: List<String>) {
        val file = File(context.filesDir, USER_FILE)
        val prefix = if (file.exists()) "\n" else ""
        file.appendText(prefix + lines.joinToString("\n") + "\n")
        parse(lines)
    }

    fun clearUserRules(context: Context) {
        val file = File(context.filesDir, USER_FILE)
        if (file.exists()) file.delete()
        load(context)
    }

    fun userRuleCount(context: Context): Int {
        val file = File(context.filesDir, USER_FILE)
        if (!file.exists()) return 0
        return runCatching { file.readLines().count { it.isNotBlank() && !it.startsWith("!") } }.getOrDefault(0)
    }

    private fun hostOf(url: String): String? {
        val start = url.indexOf("://")
        if (start < 0) return null
        val rest = url.substring(start + 3)
        val end = rest.indexOfAny(charArrayOf('/', '?', '#'))
        val host = if (end < 0) rest else rest.substring(0, end)
        val port = host.indexOf(':')
        return if (port >= 0) host.substring(0, port).lowercase() else host.lowercase()
    }
}
