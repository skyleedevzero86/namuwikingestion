package com.sleekydz86.namuwikingestion.application

data class SearchResultDto(
    val id: Long,
    val title: String,
    val contentSnippet: String,
    val similarityPercent: Double,
)
