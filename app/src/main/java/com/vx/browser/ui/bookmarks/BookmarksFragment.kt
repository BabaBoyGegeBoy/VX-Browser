package com.vx.browser.ui.bookmarks

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.vx.browser.MainActivity
import com.vx.browser.VxApplication
import com.vx.browser.data.entity.Bookmark
import com.vx.browser.data.repository.BookmarkRepository
import com.vx.browser.databinding.FragmentBookmarksBinding
import kotlinx.coroutines.launch

class BookmarksFragment : Fragment() {
    private var _binding: FragmentBookmarksBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: BookmarkRepository
    private val items = mutableListOf<Bookmark>()
    private lateinit var adapter: ArrayAdapter<Bookmark>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VxApplication
        repo = BookmarkRepository(app.database.bookmarkDao())

        adapter = object : ArrayAdapter<Bookmark>(requireContext(), android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.text = items[position].title.ifBlank { items[position].url }
                return v
            }
        }
        binding.listView.adapter = adapter

        repo.all.observe(viewLifecycleOwner, Observer { list ->
            items.clear()
            items.addAll(list)
            adapter.notifyDataSetChanged()
        })

        binding.listView.setOnItemClickListener { _, _, pos, _ ->
            (requireActivity() as MainActivity).openUrl(items[pos].url)
        }
        binding.listView.setOnItemLongClickListener { _, _, pos, _ ->
            val b = items[pos]
            AlertDialog.Builder(requireContext())
                .setTitle("删除书签")
                .setMessage(b.title.ifBlank { b.url })
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch { repo.remove(b) }
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        binding.addButton.setOnClickListener {
            (requireActivity() as MainActivity).addCurrentToBookmarks()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
