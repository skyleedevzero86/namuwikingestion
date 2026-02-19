package com.sleekydz86.namuwikingestion.infrastructure.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger {}

@Repository
class SearchUiConfigRepository(private val jdbc: JdbcTemplate) {

    fun getValue(key: String): String? = try {
        jdbc.queryForObject(
            "SELECT value FROM search_ui_config WHERE key = ?",
            String::class.java,
            key,
        )
    } catch (e: Exception) {
        logger.warn(e) { "getValue 실패, key=$key, null 반환" }
        null
    }

    fun getAllByPrefix(prefix: String): Map<String, String> = try {
        val rows = jdbc.query(
            "SELECT key, value FROM search_ui_config WHERE key LIKE ?",
            { rs, _ -> rs.getString("key") to rs.getString("value") },
            "$prefix%",
        ) ?: emptyList()
        rows.toMap()
    } catch (e: Exception) {
        logger.warn(e) { "getAllByPrefix 실패, prefix=$prefix, 빈 맵 반환" }
        emptyMap()
    }
}
