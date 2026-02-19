package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val embeddingClient: EmbeddingClient,
    private val docRepository: NamuwikiDocRepository,
    @Value("\${namuwiki.search.min-similarity-percent:55}") private val minSimilarityPercent: Double,
) {

    fun search(query: String, limit: Int = 20): List<SearchResultDto> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val vectors = embeddingClient.embedBatch(listOf(q))
        if (vectors.isEmpty()) return emptyList()
        val rows = docRepository.searchByVector(vectors.single(), limit)
        return rows
            .filter { row -> queryMatchesDocument(q, row.title, row.content) }
            .map { row ->
                val similarityPercent = cosineDistanceToSimilarityPercent(row.distance)
                SearchResultDto(
                    id = row.id,
                    title = row.title,
                    contentSnippet = snippet(row.content, 500),
                    similarityPercent = similarityPercent,
                )
            }
            .filter { it.similarityPercent >= minSimilarityPercent }
    }

    private fun queryMatchesDocument(query: String, title: String, content: String): Boolean {
        val q = query.trim()
        if (q.length > 50) return true
        val qLow = q.lowercase()
        val titleLow = title.lowercase()
        val contentLow = content.lowercase()
        return when {
            qLow.length <= 30 -> qLow in titleLow || qLow in contentLow
            else -> qLow.split(Regex("\\s+")).any { word ->
                word.length > 1 && (word in titleLow || word in contentLow)
            }
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
