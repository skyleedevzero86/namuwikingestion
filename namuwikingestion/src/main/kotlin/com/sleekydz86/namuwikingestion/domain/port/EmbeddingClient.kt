package com.sleekydz86.namuwikingestion.domain.port

interface EmbeddingClient {
    fun embedBatch(texts: List<String>): List<FloatArray>
}
