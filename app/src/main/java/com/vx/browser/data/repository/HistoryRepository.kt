package com.vx.browser.data.repository

import androidx.lifecycle.LiveData
import com.vx.browser.data.dao.HistoryDao
import com.vx.browser.data.entity.History

class HistoryRepository(private val dao: HistoryDao) {
    val all: LiveData<List<History>> = dao.observeAll()
    suspend fun add(title: String, url: String) = dao.insert(History(title = title, url = url))
    suspend fun remove(h: History) = dao.delete(h)
    suspend fun clear() = dao.clear()
}
