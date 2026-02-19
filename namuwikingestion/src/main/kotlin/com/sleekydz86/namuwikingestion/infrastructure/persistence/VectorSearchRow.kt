package com.sleekydz86.namuwikingestion.infrastructure.persistence

data class VectorSearchRow(
    val id: Long,
    val title: String,
    val content: String,
    val distance: Double,
)
