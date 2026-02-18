package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val embeddingClient: EmbeddingClient,
    private val docRepository: NamuwikiDocRepository,
) {

    fun search(query: String, limit: Int = 20): List<SearchResultDto> {
        if (query.isBlank()) return emptyList()
        val vectors = embeddingClient.embedBatch(listOf(query.trim()))
        if (vectors.isEmpty()) return emptyList()
        val rows = docRepository.searchByVector(vectors.single(), limit)
        return rows.map { row ->
            val similarityPercent = cosineDistanceToSimilarityPercent(row.distance)
            SearchResultDto(
                id = row.id,
                title = row.title,
                contentSnippet = snippet(row.content, 500),
                similarityPercent = similarityPercent,
            )
        }
    }

    private fun cosineDistanceToSimilarityPercent(distance: Double): Double {
        if (distance.isNaN()) return 0.0
        val similarity = (2.0 - distance.coerceIn(0.0, 2.0)) / 2.0
        return (similarity * 100.0).coerceIn(0.0, 100.0)
    }

    private fun snippet(text: String, maxLen: Int): String {
        if (text.length <= maxLen) return text
        return text.take(maxLen) + "..."
    }

    data class SearchResultDto(
        val id: Long,
        val title: String,
        val contentSnippet: String,
        val similarityPercent: Double,
    )
}
