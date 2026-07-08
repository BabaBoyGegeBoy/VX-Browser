package com.vx.browser.ui.history

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
import com.vx.browser.data.entity.History
import com.vx.browser.data.repository.HistoryRepository
import com.vx.browser.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: HistoryRepository
    private val items = mutableListOf<History>()
    private lateinit var adapter: ArrayAdapter<History>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VxApplication
        repo = HistoryRepository(app.database.historyDao())

        adapter = object : ArrayAdapter<History>(requireContext(), android.R.layout.simple_list_item_1, items) {
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

        binding.clearButton.setOnClickListener {
            lifecycleScope.launch { repo.clear() }
            Toast.makeText(requireContext(), "历史已清空", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
