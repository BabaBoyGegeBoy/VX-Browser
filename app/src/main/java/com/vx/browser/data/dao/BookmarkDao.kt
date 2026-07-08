package com.vx.browser.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.vx.browser.data.entity.Bookmark

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): LiveData<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: Bookmark)

    @Delete
    suspend fun delete(b: Bookmark)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}
