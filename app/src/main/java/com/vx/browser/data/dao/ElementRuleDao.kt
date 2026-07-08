package com.vx.browser.data.dao

import androidx.room.*
import com.vx.browser.data.entity.ElementRule

@Dao
interface ElementRuleDao {
    @Query("SELECT * FROM element_rules WHERE host = :host")
    suspend fun getByHost(host: String): List<ElementRule>

    @Insert
    suspend fun insert(rule: ElementRule)

    @Query("DELETE FROM element_rules WHERE host = :host")
    suspend fun clearHost(host: String)
}
