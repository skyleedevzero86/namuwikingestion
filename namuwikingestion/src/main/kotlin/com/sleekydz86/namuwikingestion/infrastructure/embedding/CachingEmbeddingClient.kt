package com.sleekydz86.namuwikingestion.infrastructure.embedding

import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import java.util.Collections.synchronizedMap
import java.util.LinkedHashMap

class CachingEmbeddingClient(
    private val delegate: EmbeddingClient,
    private val maxSize: Int = 200,
) : EmbeddingClient {

    private val cache = synchronizedMap(
        object : LinkedHashMap<String, FloatArray>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > maxSize
        }
    )

    override fun embedBatch(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        if (texts.size == 1) {
            val key = texts.single()
            cache[key]?.let { return listOf(it) }
            val result = delegate.embedBatch(texts)
            result.singleOrNull()?.let { cache[key] = it.copyOf() }
            return result
        }
        return delegate.embedBatch(texts)
    }
}
