package com.vx.browser.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "vx_prefs"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isJavaScriptEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("js_enabled", true)
    fun setJavaScriptEnabled(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("js_enabled", value).apply()

    fun isNightMode(ctx: Context): Boolean = sp(ctx).getBoolean("night_mode", false)
    fun setNightMode(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("night_mode", value).apply()

    fun searchEngine(ctx: Context): String =
        sp(ctx).getString("search_engine", "https://www.bing.com/search?q=")
            ?: "https://www.bing.com/search?q="

    fun isAdBlockEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("ad_block", true)
    fun setAdBlockEnabled(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("ad_block", value).apply()

    fun isSnifferEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("sniffer", true)
    fun setSnifferEnabled(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("sniffer", value).apply()

    fun isBlockPopup(ctx: Context): Boolean = sp(ctx).getBoolean("block_popup", false)
    fun setBlockPopup(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("block_popup", value).apply()

    fun isBlockAutoRedirect(ctx: Context): Boolean = sp(ctx).getBoolean("block_autoredirect", false)
    fun setBlockAutoRedirect(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("block_autoredirect", value).apply()

    private const val KEY_JUMP_CONFIRM = "jump_confirm_hosts"
    fun jumpConfirmHosts(ctx: Context): MutableSet<String> =
        (sp(ctx).getStringSet(KEY_JUMP_CONFIRM, emptySet()) ?: emptySet()).toMutableSet()
    fun setJumpConfirmHosts(ctx: Context, set: Set<String>) =
        sp(ctx).edit().putStringSet(KEY_JUMP_CONFIRM, set).apply()
    fun isJumpConfirm(ctx: Context, host: String?): Boolean =
        host != null && jumpConfirmHosts(ctx).contains(host)
}
