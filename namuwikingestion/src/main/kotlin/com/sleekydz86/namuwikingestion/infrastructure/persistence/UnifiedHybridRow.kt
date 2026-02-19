package com.sleekydz86.namuwikingestion.infrastructure.persistence

data class UnifiedHybridRow(
    val id: Long,
    val title: String,
    val content: String,
    val totalScore: Double,
)
