package com.vx.browser.data.repository

import androidx.lifecycle.LiveData
import com.vx.browser.data.dao.BookmarkDao
import com.vx.browser.data.entity.Bookmark

class BookmarkRepository(private val dao: BookmarkDao) {
    val all: LiveData<List<Bookmark>> = dao.observeAll()
    suspend fun add(title: String, url: String) = dao.insert(Bookmark(title = title, url = url))
    suspend fun remove(b: Bookmark) = dao.delete(b)
    suspend fun clear() = dao.clear()
}
