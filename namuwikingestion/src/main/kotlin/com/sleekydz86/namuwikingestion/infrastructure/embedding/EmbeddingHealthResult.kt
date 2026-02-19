package com.sleekydz86.namuwikingestion.infrastructure.embedding

data class EmbeddingHealthResult(
    val ok: Boolean,
    val healthUrl: String?,
    val model: String?,
)
