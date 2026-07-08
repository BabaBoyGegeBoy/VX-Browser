package com.vx.browser.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.vx.browser.data.entity.History

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): LiveData<List<History>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(h: History)

    @Delete
    suspend fun delete(h: History)

    @Query("DELETE FROM history")
    suspend fun clear()
}
