package com.vx.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vx.browser.data.dao.BookmarkDao
import com.vx.browser.data.dao.ElementRuleDao
import com.vx.browser.data.dao.HistoryDao
import com.vx.browser.data.entity.Bookmark
import com.vx.browser.data.entity.ElementRule
import com.vx.browser.data.entity.History

@Database(entities = [Bookmark::class, History::class, ElementRule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun elementRuleDao(): ElementRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?:                 Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vx_browser.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
