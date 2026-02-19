package com.sleekydz86.namuwikingestion.infrastructure.persistence

data class FullTextSearchRow(
    val id: Long,
    val title: String,
    val content: String,
    val rank: Double,
)
