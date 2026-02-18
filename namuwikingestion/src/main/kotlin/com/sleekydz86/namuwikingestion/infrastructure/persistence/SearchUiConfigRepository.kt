package com.sleekydz86.namuwikingestion.infrastructure.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SearchUiConfigRepository(private val jdbc: JdbcTemplate) {

    fun getValue(key: String): String? = try {
        jdbc.queryForObject(
            "SELECT value FROM search_ui_config WHERE key = ?",
            String::class.java,
            key,
        )
    } catch (_: Exception) {
        null
    }

    fun getAllByPrefix(prefix: String): Map<String, String> = try {
        val rows = jdbc.query(
            "SELECT key, value FROM search_ui_config WHERE key LIKE ?",
            { rs, _ -> rs.getString("key") to rs.getString("value") },
            "$prefix%",
        ) ?: emptyList()
        rows.toMap()
    } catch (_: Exception) {
        emptyMap()
    }
}
