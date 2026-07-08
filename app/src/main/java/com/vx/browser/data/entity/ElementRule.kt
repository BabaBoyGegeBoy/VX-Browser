package com.vx.browser.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "element_rules")
data class ElementRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val selector: String,
    val action: String // "hide" | "delete"
)
