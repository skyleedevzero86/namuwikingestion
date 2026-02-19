package com.sleekydz86.namuwikingestion.global.util

object SearchKeywordTokenizer {

    private val whitespace = Regex("\\s+")

    fun toTokens(query: String?): List<String> {
        if (query.isNullOrBlank()) return emptyList()
        return query.trim()
            .split(whitespace)
            .map { segment -> segment.trim().removePrefix("#").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
