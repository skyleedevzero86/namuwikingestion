package com.sleekydz86.namuwikingestion.application

data class HybridSearchResult(
    val results: List<HybridSearchResultDto>,
    val generatedSql: String,
    val queryExplanation: String,
)
