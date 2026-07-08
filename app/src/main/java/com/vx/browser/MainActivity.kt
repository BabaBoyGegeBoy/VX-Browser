package com.vx.browser

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.vx.browser.browser.AdBlock
import com.vx.browser.browser.MediaSniffer
import com.vx.browser.browser.MediaProbe
import com.vx.browser.browser.Tab
import com.vx.browser.browser.TabManager
import com.vx.browser.browser.VxWebChromeClient
import com.vx.browser.browser.VxWebViewClient
import com.vx.browser.data.repository.BookmarkRepository
import com.vx.browser.data.repository.ElementRuleRepository
import com.vx.browser.data.repository.HistoryRepository
import com.vx.browser.databinding.ActivityMainBinding
import com.vx.browser.ui.bookmarks.BookmarksFragment
import com.vx.browser.ui.history.HistoryFragment
import com.vx.browser.ui.home.HomePage
import com.vx.browser.ui.settings.SettingsFragment
import com.vx.browser.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabManager: TabManager
    private lateinit var bookmarkRepo: BookmarkRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var elementRepo: ElementRuleRepository
    private var isHome = false
    private var inSelectorMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.isNightMode(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as VxApplication
        bookmarkRepo = BookmarkRepository(app.database.bookmarkDao())
        historyRepo = HistoryRepository(app.database.historyDao())
        elementRepo = ElementRuleRepository(app.database.elementRuleDao())
        tabManager = TabManager(this)

        AdBlock.enabled = Prefs.isAdBlockEnabled(this)
        MediaSniffer.enabled = Prefs.isSnifferEnabled(this)

        binding.swipeRefresh.setOnRefreshListener {
            val wv = tabManager.getCurrent()?.webView
            if (isHome) showHome() else wv?.reload()
        }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            tabManager.getCurrent()?.webView?.canScrollVertically(-1) ?: false
        }

        newTabAndConfigure()
        setupAddressBar()
        setupBottomBar()
        setupSelectorBar()
        showHome()
    }

    private fun newTabAndConfigure(): Tab {
        val tab = tabManager.newTab()
        tab.webView.webViewClient = VxWebViewClient(
            onTitle = { t -> tab.title = t },
            onUrl = { u ->
                tab.url = u
                binding.swipeRefresh.isRefreshing = false
                if (tabManager.getCurrent() == tab) {
                    binding.addressBar.setText(u)
                    if (u == "vx://home") {
                        binding.addressBar.visibility = View.GONE
                        isHome = true
                    } else {
                        binding.addressBar.visibility = View.VISIBLE
                        isHome = false
                    }
                }
            },
            historyRepo = historyRepo,
            elementRepo = elementRepo,
            onCustomScheme = ::handleCustomScheme
        )
        tab.webView.webChromeClient = VxWebChromeClient { p ->
            binding.progressBar.progress = p
            binding.progressBar.isVisible = p < 100
        }
        tab.webView.setDownloadListener { url, userAgent, _, _, _ ->
            if (!url.isNullOrEmpty()) handleDownload(url, userAgent)
        }
        return tab
    }

    fun openUrl(input: String) {
        isHome = false
        val url = normalize(input)
        var tab = tabManager.getCurrent()
        if (tab == null) tab = newTabAndConfigure()
        tab.url = url
        tab.webView.loadUrl(url)
        showBrowser()
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("about:")) return s
        if (s.contains(".") && !s.contains(" ")) return "https://$s"
        return "${Prefs.searchEngine(this)}${Uri.encode(s)}"
    }

    private fun showHome() {
        isHome = true
        binding.addressBar.visibility = View.GONE
        val tab = tabManager.getCurrent() ?: newTabAndConfigure()
        val bookmarks = bookmarkRepo.all.value.orEmpty()
        val html = HomePage.build(bookmarks, Prefs.isNightMode(this))
        binding.content.removeAllViews()
        binding.content.addView(
            tab.webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        tab.webView.loadDataWithBaseURL("vx://home", html, "text/html", "utf-8", null)
    }

    private fun showFragment(frag: Fragment) {
        binding.content.removeAllViews()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, frag, "page")
            .commit()
    }

    private fun showBrowser() {
        isHome = false
        supportFragmentManager.findFragmentById(R.id.content)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        binding.content.removeAllViews()
        val wv = tabManager.getCurrent()?.webView ?: return
        binding.addressBar.visibility = View.VISIBLE
        binding.content.addView(
            wv,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val txt = binding.addressBar.text.toString()
                if (txt.isNotBlank()) openUrl(txt)
                binding.addressBar.clearFocus()
                true
            } else false
        }
        binding.addressBar.onFocusChangeListener = View.OnFocusChangeListener { _: View?, hasFocus: Boolean ->
            binding.addressBar.isCursorVisible = hasFocus
        }
    }

    private fun setupBottomBar() {
        binding.btnBack.setOnClickListener {
            val wv = tabManager.getCurrent()?.webView
            if (wv?.canGoBack() == true) wv.goBack() else showHome()
        }
        binding.btnForward.setOnClickListener {
            tabManager.getCurrent()?.webView?.goForward()
        }
        binding.btnHome.setOnClickListener { showHome() }
        binding.btnTabs.setOnClickListener { openTabsDialog() }
        binding.btnMenu.setOnClickListener { openMenu() }
    }

    private fun setupSelectorBar() {
        binding.selParent.setOnClickListener {
            curWebView()?.evaluateJavascript("if(window.vxSelector)window.vxSelector.parent();", null)
        }
        binding.selChild.setOnClickListener {
            curWebView()?.evaluateJavascript("if(window.vxSelector)window.vxSelector.child();", null)
        }
        binding.selClear.setOnClickListener {
            curWebView()?.evaluateJavascript("if(window.vxSelector)window.vxSelector.clearAll();", null)
        }
        binding.selHide.setOnClickListener { applySelector("hide") }
        binding.selDelete.setOnClickListener { applySelector("delete") }
        binding.selDone.setOnClickListener { exitAdMarkMode() }
    }

    private fun curWebView() = tabManager.getCurrent()?.webView

    private fun enterAdMarkMode() {
        val tab = tabManager.getCurrent() ?: return
        val url = tab.url
        if (!url.startsWith("http")) {
            Toast.makeText(this, "请在网页中使用标记广告", Toast.LENGTH_SHORT).show()
            return
        }
        if (inSelectorMode) return
        inSelectorMode = true
        binding.selectorBar.visibility = View.VISIBLE
        tab.webView.evaluateJavascript(VxWebViewClient.ELEMENT_SELECTOR_JS, null)
        Toast.makeText(this, "点击元素标记广告（可多选）；上一级/下一级微调，隐藏或删除", Toast.LENGTH_LONG).show()
    }

    private fun exitAdMarkMode() {
        if (!inSelectorMode) return
        inSelectorMode = false
        binding.selectorBar.visibility = View.GONE
        curWebView()?.evaluateJavascript("if(window.vxSelector)window.vxSelector.exit();", null)
    }

    private fun applySelector(action: String) {
        val wv = curWebView() ?: return
        val host = Uri.parse(tabManager.getCurrent()?.url ?: "").host ?: return
        wv.evaluateJavascript("if(window.vxSelector)window.vxSelector.apply('$action');else'[]'") { json ->
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return@evaluateJavascript
            if (arr.length() == 0) {
                runOnUiThread { Toast.makeText(this@MainActivity, "未选择任何元素", Toast.LENGTH_SHORT).show() }
                return@evaluateJavascript
            }
            lifecycleScope.launch {
                repeat(arr.length()) { i -> elementRepo.add(host, arr.getString(i), action) }
            }
            val verb = if (action == "hide") "隐藏" else "删除"
            runOnUiThread {
                Toast.makeText(this@MainActivity, "已记录 ${arr.length()} 条${verb}规则（复访自动生效）", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openTabsDialog() {
        val tabs = tabManager.list()
        val items = tabs.map {
            val t = it.title.ifBlank { it.url.ifBlank { "空白页" } }
            if (t.length > 40) t.take(40) + "…" else t
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("标签页 (${tabs.size})")
            .setItems(items) { _, which ->
                tabManager.select(which)
                showBrowser()
            }
            .setPositiveButton("新建") { _, _ ->
                newTabAndConfigure()
                showBrowser()
            }
            .setNeutralButton("关闭当前") { _, _ ->
                val idx = tabManager.currentIndex
                if (idx >= 0) {
                    tabManager.close(idx)
                    if (tabManager.list().isEmpty()) showHome() else showBrowser()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openMenu() {
        val url = tabManager.getCurrent()?.url ?: ""
        val host = if (url.startsWith("http")) Uri.parse(url).host else null
        val jcOn = host != null && Prefs.isJumpConfirm(this, host)
        val jcLabel = "跳转确认（${if (jcOn) "开" else "关"}）"
        val options = arrayOf("书签", "历史", "嗅探", "标记广告", jcLabel, "设置")
        AlertDialog.Builder(this)
            .setTitle("菜单")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFragment(BookmarksFragment())
                    1 -> showFragment(HistoryFragment())
                    2 -> showSnifferDialog()
                    3 -> enterAdMarkMode()
                    4 -> toggleJumpConfirm()
                    5 -> showFragment(SettingsFragment())
                }
            }
            .show()
    }

    private fun toggleJumpConfirm() {
        val url = tabManager.getCurrent()?.url ?: ""
        if (!url.startsWith("http")) {
            Toast.makeText(this, "请在网页中使用跳转确认", Toast.LENGTH_SHORT).show()
            return
        }
        val host = Uri.parse(url).host ?: return
        val set = Prefs.jumpConfirmHosts(this)
        val msg = if (set.contains(host)) {
            set.remove(host)
            "已关闭本页跳转确认"
        } else {
            set.add(host)
            "已开启本页跳转确认（异站跳转需确认）"
        }
        Prefs.setJumpConfirmHosts(this, set)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private data class SniffRow(var url: String, var meta: MediaProbe.MediaMeta? = null)

    private fun showSnifferDialog() {
        val items = MediaSniffer.list()
        if (items.isEmpty()) {
            Toast.makeText(this, "暂无嗅探到的媒体链接", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = items.map { SniffRow(it) }.toMutableList()
        val displayed = rows.toMutableList()

        // 扩展名选项（优先按 URL 路径收集）
        val extSet = mutableSetOf<String>()
        for (r in rows) {
            val e = urlExt(r.url)
            if (e.isNotEmpty()) extSet.add(e)
        }
        val extOptions = mutableListOf("全部").apply {
            addAll(extSet.sorted())
            add("其他")
        }
        var extSel = "全部"

        val sizeOptions = listOf("全部", "≥1 MB", "≥10 MB", "≥100 MB", "≥1 GB")
        val sizeThresholds = listOf(
            0L, 1L * 1024 * 1024, 10L * 1024 * 1024,
            100L * 1024 * 1024, 1024L * 1024 * 1024
        )
        var sizeIdx = 0

        val typeOptions = listOf("全部", "视频", "音频", "HLS", "DASH", "其他")
        var typeSel = "全部"

        val sniffAdapter = object : BaseAdapter() {
            override fun getCount() = displayed.size
            override fun getItem(position: Int) = displayed[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = displayed[position]
                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 14, 28, 14)
                }
                val tvUrl = TextView(this@MainActivity).apply {
                    text = row.url
                    textSize = 13f
                    setTextColor(Color.parseColor("#FF888888"))
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val tvMeta = TextView(this@MainActivity).apply {
                    text = metaText(row)
                    textSize = 12f
                    setTextColor(Color.GRAY)
                    setPadding(0, 6, 0, 0)
                }
                container.addView(tvUrl)
                container.addView(tvMeta)
                return container
            }
        }

        fun applyFilter() {
            displayed.clear()
            for (r in rows) {
                val e = r.meta?.ext?.takeIf { it.isNotEmpty() } ?: urlExt(r.url)
                if (extSel != "全部") {
                    if (extSel == "其他") {
                        if (e.isNotEmpty()) continue
                    } else if (e != extSel) continue
                }
                if (typeSel != "全部" && typeOf(r) != typeSel) continue
                if (sizeIdx > 0) {
                    val size = r.meta?.size ?: -1
                    if (size < 0 || size < sizeThresholds[sizeIdx]) continue
                }
                displayed.add(r)
            }
            sniffAdapter.notifyDataSetChanged()
        }

        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 12, 28, 12)
        }

        fun mkSpinner(opts: List<String>): Spinner {
            return Spinner(ctx).apply {
                adapter = ArrayAdapter(
                    ctx, android.R.layout.simple_spinner_item, opts
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
        }
        fun mkLabel(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setPadding(0, 0, 8, 0)
        }
        val wrap = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        val spExt = mkSpinner(extOptions)
        val spSize = mkSpinner(sizeOptions)
        row1.addView(mkLabel("扩展名"))
        row1.addView(spExt, wrap)
        row1.addView(mkLabel("大小").apply { setPadding(16, 0, 8, 0) })
        row1.addView(spSize, wrap)
        root.addView(row1)

        val row2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        val spType = mkSpinner(typeOptions)
        row2.addView(mkLabel("类型"))
        row2.addView(spType, wrap)
        root.addView(row2)

        val listH = (320 * resources.displayMetrics.density).toInt()
        val listView = ListView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listH)
        }
        listView.adapter = sniffAdapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            showSniffItem(displayed[pos].url)
        }
        root.addView(listView)

        spExt.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                extSel = extOptions[pos]; applyFilter()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        spSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                sizeIdx = pos; applyFilter()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                typeSel = typeOptions[pos]; applyFilter()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        AlertDialog.Builder(this)
            .setTitle("嗅探到的媒体 (${rows.size})")
            .setView(root)
            .setPositiveButton("清空") { _, _ ->
                MediaSniffer.clear()
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()

        rows.forEach { row ->
            lifecycleScope.launch(Dispatchers.IO) {
                val meta = MediaProbe.fetchMeta(row.url)
                row.meta = meta
                runOnUiThread { applyFilter() }
            }
        }
    }

    private fun urlExt(url: String): String {
        val path = Uri.parse(url).path ?: ""
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        if (dot <= slash || dot >= path.length - 1) return ""
        return path.substring(dot + 1).lowercase()
    }

    private fun typeOf(row: SniffRow): String {
        val m = row.meta ?: return "其他"
        return when {
            m.ext == "m3u8" || m.ext == "m3u" -> "HLS"
            m.ext == "mpd" -> "DASH"
            m.contentType.contains("video", true) -> "视频"
            m.contentType.contains("audio", true) -> "音频"
            m.ext.isNotEmpty() -> "其他"
            else -> "其他"
        }
    }

    private fun metaText(row: SniffRow): String {
        val m = row.meta ?: return "解析中…"
        val type = when {
            m.ext == "m3u8" || m.ext == "m3u" -> "HLS"
            m.ext == "mpd" -> "DASH"
            m.contentType.contains("video") -> "视频"
            m.contentType.contains("audio") -> "音频"
            m.ext.isNotEmpty() -> m.ext
            else -> "媒体"
        }
        val parts = mutableListOf(type)
        if (m.size > 0) parts.add(MediaProbe.humanSize(m.size))
        if (m.ext.isNotEmpty()) parts.add(m.ext)
        if (m.width > 0 && m.height > 0) parts.add("${m.width}x${m.height}")
        return parts.joinToString("  •  ")
    }

    private fun showSniffItem(url: String) {
        AlertDialog.Builder(this)
            .setTitle("媒体链接")
            .setMessage(url)
            .setPositiveButton("复制") { _, _ ->
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("url", url))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("用IDM+下载") { _, _ -> handleDownload(url, null) }
            .setNeutralButton("取消", null)
            .show()
    }

    fun getCurrentTabInfo(): Pair<String, String>? {
        val t = tabManager.getCurrent() ?: return null
        return (t.title to t.url)
    }

    fun addCurrentToBookmarks() {
        val info = getCurrentTabInfo() ?: return
        val (title, url) = info
        if (url.isBlank() || url.startsWith("about:")) {
            Toast.makeText(this, "当前页面无法收藏", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch { bookmarkRepo.add(title.ifBlank { url }, url) }
        Toast.makeText(this, "已加入书签", Toast.LENGTH_SHORT).show()
    }

    private fun handleCustomScheme(raw: String) {
        val uri = Uri.parse(raw)
        if (uri.scheme != "vx") return
        when (uri.host) {
            "search" -> {
                val q = uri.getQueryParameter("q") ?: return
                openUrl(q)
            }
            "open" -> {
                val u = uri.getQueryParameter("url") ?: return
                openUrl(u)
            }
            "media" -> {
                val u = uri.getQueryParameter("url") ?: return
                MediaSniffer.add(u)
            }
            "qr" -> Toast.makeText(this, getString(R.string.scan_todo), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDownload(url: String, userAgent: String?) {
        val idm = "idm.internet.download.manager.plus"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(idm)
            putExtra("extra_user_agent", userAgent)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val chooser = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (chooser.resolveActivity(packageManager) != null) {
                startActivity(chooser)
            } else {
                Toast.makeText(this, "未找到可用下载器，请安装 IDM+ 等", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (inSelectorMode) {
            exitAdMarkMode()
            return
        }
        val frag = supportFragmentManager.findFragmentById(R.id.content)
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitNow()
            if (isHome) showHome() else showBrowser()
            return
        }
        val wv = tabManager.getCurrent()?.webView
        if (wv?.canGoBack() == true) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
