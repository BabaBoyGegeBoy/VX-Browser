package com.vx.browser.data.repository

import com.vx.browser.data.dao.ElementRuleDao
import com.vx.browser.data.entity.ElementRule

class ElementRuleRepository(private val dao: ElementRuleDao) {
    suspend fun getByHost(host: String) = dao.getByHost(host)
    suspend fun add(host: String, selector: String, action: String) =
        dao.insert(ElementRule(host = host, selector = selector, action = action))
    suspend fun clearHost(host: String) = dao.clearHost(host)
}
