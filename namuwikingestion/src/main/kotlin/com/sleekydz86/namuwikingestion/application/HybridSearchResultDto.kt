package com.sleekydz86.namuwikingestion.application

data class HybridSearchResultDto(
    val id: Long,
    val title: String,
    val contentSnippet: String,
    val similarityPercent: Double,
    val totalScore: Double,
)
