package com.vx.browser.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vx.browser.VxApplication
import com.vx.browser.browser.AdBlock
import com.vx.browser.browser.MediaSniffer
import com.vx.browser.data.repository.BookmarkRepository
import com.vx.browser.data.repository.HistoryRepository
import com.vx.browser.databinding.FragmentSettingsBinding
import com.vx.browser.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val pickRuleFiles = registerForActivityResult(
        object : ActivityResultContract<Array<String>, List<Uri>>() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    putExtra(Intent.EXTRA_MIME_TYPES, input)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
                if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
                val clip = intent.clipData
                if (clip != null) {
                    val list = mutableListOf<Uri>()
                    for (i in 0 until clip.itemCount) list.add(clip.getItemAt(i).uri)
                    return list
                }
                val single = intent.data
                return if (single != null) listOf(single) else emptyList()
            }
        }
    ) { uris -> importRuleUris(uris) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        binding.switchJs.isChecked = Prefs.isJavaScriptEnabled(ctx)
        binding.switchJs.setOnCheckedChangeListener { _, checked ->
            Prefs.setJavaScriptEnabled(ctx, checked)
            Toast.makeText(ctx, "新建标签页后生效", Toast.LENGTH_SHORT).show()
        }

        binding.switchNight.isChecked = Prefs.isNightMode(ctx)
        binding.switchNight.setOnCheckedChangeListener { _, checked ->
            Prefs.setNightMode(ctx, checked)
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.switchAd.isChecked = Prefs.isAdBlockEnabled(ctx)
        binding.switchAd.setOnCheckedChangeListener { _, checked ->
            Prefs.setAdBlockEnabled(ctx, checked)
            AdBlock.enabled = checked
        }

        binding.switchSniffer.isChecked = Prefs.isSnifferEnabled(ctx)
        binding.switchSniffer.setOnCheckedChangeListener { _, checked ->
            Prefs.setSnifferEnabled(ctx, checked)
            MediaSniffer.enabled = checked
        }

        binding.switchBlockPopup.isChecked = Prefs.isBlockPopup(ctx)
        binding.switchBlockPopup.setOnCheckedChangeListener { _, checked ->
            Prefs.setBlockPopup(ctx, checked)
        }

        binding.switchBlockRedirect.isChecked = Prefs.isBlockAutoRedirect(ctx)
        binding.switchBlockRedirect.setOnCheckedChangeListener { _, checked ->
            Prefs.setBlockAutoRedirect(ctx, checked)
        }

        binding.importRules.setOnClickListener { showImportDialog() }

        binding.clearUserRules.setOnClickListener {
            AdBlock.clearUserRules(ctx)
            Toast.makeText(ctx, "自定义规则已清空", Toast.LENGTH_SHORT).show()
        }

        binding.clearSniffer.setOnClickListener {
            MediaSniffer.clear()
            Toast.makeText(ctx, "嗅探记录已清空", Toast.LENGTH_SHORT).show()
        }

        binding.clearHistory.setOnClickListener {
            val repo = HistoryRepository((requireActivity().application as VxApplication).database.historyDao())
            lifecycleScope.launch { repo.clear() }
            Toast.makeText(ctx, "历史已清空", Toast.LENGTH_SHORT).show()
        }

        binding.clearBookmarks.setOnClickListener {
            val repo = BookmarkRepository((requireActivity().application as VxApplication).database.bookmarkDao())
            lifecycleScope.launch { repo.clear() }
            Toast.makeText(ctx, "书签已清空", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importRuleUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = requireContext()
        val all = mutableListOf<String>()
        var files = 0
        for (u in uris) {
            val lines = runCatching {
                ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readLines() }
            }.getOrNull()
            if (!lines.isNullOrEmpty()) {
                all.addAll(lines)
                files++
            }
        }
        if (all.isEmpty()) {
            Toast.makeText(ctx, "未读取到规则", Toast.LENGTH_SHORT).show()
            return
        }
        AdBlock.importRules(ctx, all)
        Toast.makeText(ctx, "已导入 ${all.size} 条规则（${files} 个文件）", Toast.LENGTH_SHORT).show()
    }

    private fun showImportDialog() {
        val items = arrayOf("从文件导入（可多选）", "从 URL 导入（每行一个）")
        AlertDialog.Builder(requireContext())
            .setTitle("导入拦截规则")
            .setItems(items) { _, which ->
                if (which == 0) {
                    pickRuleFiles.launch(arrayOf("text/*", "*/*"))
                } else {
                    showUrlImportDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showUrlImportDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply { hint = "每行一个规则文件 URL" }
        AlertDialog.Builder(ctx)
            .setTitle("从 URL 导入规则")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val urls = input.text.toString().lineSequence().map { it.trim() }
                    .filter { it.startsWith("http") }.toList()
                if (urls.isEmpty()) return@setPositiveButton
                lifecycleScope.launch(Dispatchers.IO) {
                    val all = mutableListOf<String>()
                    var ok = 0
                    for (u in urls) {
                        val lines = downloadText(u)
                        if (!lines.isNullOrEmpty()) {
                            all.addAll(lines)
                            ok++
                        }
                    }
                    launch(Dispatchers.Main) {
                        if (all.isNotEmpty()) {
                            AdBlock.importRules(ctx, all)
                            Toast.makeText(ctx, "已导入 ${all.size} 条规则（${ok} 个URL）", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "下载失败或内容为空", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadText(url: String): List<String>? {
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.inputStream.bufferedReader().use { it.readLines() }
        }.getOrNull()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
