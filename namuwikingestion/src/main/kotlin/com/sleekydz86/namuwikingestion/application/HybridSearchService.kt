package com.sleekydz86.namuwikingestion.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import com.sleekydz86.namuwikingestion.infrastructure.persistence.SearchUiConfigRepository
import org.springframework.stereotype.Service

@Service
class HybridSearchService(
    private val embeddingClient: EmbeddingClient,
    private val docRepository: NamuwikiDocRepository,
    private val searchUiConfigRepository: SearchUiConfigRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    fun search(
        query: String,
        vectorMode: String,
        enableBm25: Boolean,
        keywordWeight: Double,
        limit: Int = 20,
    ): HybridSearchResult {
        val q = query.trim()
        if (q.isBlank()) {
            val emptySql = searchUiConfigRepository.getValue("search2.emptySqlPlaceholder") ?: ""
            val emptyExplain = searchUiConfigRepository.getValue("search2.emptyExplanationPlaceholder") ?: "[]"
            return HybridSearchResult(emptyList(), emptySql, emptyExplain)
        }

        val useVector = vectorMode != "NONE"
        val vectorLimit = limit.coerceAtLeast(10).coerceAtMost(50)
        var vectorExplainJson = "[]"
        val vectorRows = if (useVector) {
            val vectors = embeddingClient.embedBatch(listOf(q))
            if (vectors.isEmpty()) emptyList() else {
                vectorExplainJson = try {
                    docRepository.explainVectorSearch(vectors.single(), vectorLimit)
                } catch (_: Exception) {
                    "[]"
                }
                docRepository.searchByVector(vectors.single(), 50)
            }
        } else emptyList()

        val textRows = if (enableBm25) {
            docRepository.searchByFullText(q, 50)
        } else emptyList()

        val fulltextExplainJson = if (enableBm25) docRepository.explainFullTextSearch(q, 50, "simple") else "[]"
        val queryExplanation = mergeExplainJson(vectorExplainJson, fulltextExplainJson)
        val generatedSql = buildGeneratedSqlFromRepository(q, useVector, enableBm25)

        val semanticWeight = 1.0 - keywordWeight.coerceIn(0.0, 1.0)
        val kwWeight = keywordWeight.coerceIn(0.0, 1.0)

        data class DocInfo(val id: Long, val title: String, val content: String, val distance: Double)
        val scoresById = mutableMapOf<Long, Pair<Double, DocInfo>>()

        vectorRows.forEach { row ->
            val sim = (2.0 - row.distance.coerceIn(0.0, 2.0)) / 2.0
            val score = semanticWeight * sim
            scoresById[row.id] = (scoresById[row.id]?.first?.plus(score) ?: score) to DocInfo(row.id, row.title, row.content, row.distance)
        }

        textRows.forEach { row ->
            val rank = row.rank.coerceAtLeast(0.0)
            val normRank = 1.0 / (60.0 + rank)
            val score = kwWeight * normRank * 61.0
            val current = scoresById[row.id]
            val docInfo = current?.second ?: DocInfo(row.id, row.title, row.content, 1.0)
            scoresById[row.id] = ((current?.first ?: 0.0) + score) to docInfo
        }

        val sorted = scoresById.entries
            .sortedByDescending { it.value.first }
            .take(limit)
            .map { (_, pair) ->
                val (totalScore, doc) = pair
                val similarityPercent = ((2.0 - doc.distance.coerceIn(0.0, 2.0)) / 2.0 * 100.0).coerceIn(0.0, 100.0)
                HybridSearchResultDto(
                    id = doc.id,
                    title = doc.title,
                    contentSnippet = doc.content.take(500).let { if (it.length >= 500) "$it..." else it },
                    similarityPercent = similarityPercent,
                    totalScore = totalScore,
                )
            }

        return HybridSearchResult(sorted, generatedSql, queryExplanation)
    }

    private fun mergeExplainJson(vectorExplain: String, fulltextExplain: String): String {
        if (vectorExplain == "[]" && fulltextExplain == "[]") return "[]"
        if (vectorExplain == "[]") return fulltextExplain
        if (fulltextExplain == "[]") return vectorExplain
        return try {
            val v: List<Any> = objectMapper.readValue(vectorExplain)
            val f: List<Any> = objectMapper.readValue(fulltextExplain)
            objectMapper.writeValueAsString(v + f)
        } catch (_: Exception) {
            "[$vectorExplain, $fulltextExplain]"
        }
    }

    private fun buildGeneratedSqlFromRepository(query: String, useVector: Boolean, useBm25: Boolean): String {
        val sqlParts = mutableListOf<String>()
        if (useVector) {
            sqlParts.add("-- Vector (embedding from embedding-server)\n" + docRepository.getVectorSearchSqlForDisplay(50))
        }
        if (useBm25) {
            sqlParts.add("-- Full-text (BM25)\n" + docRepository.getFullTextSearchSqlForDisplay(50, "simple", query))
        }
        return when {
            sqlParts.isNotEmpty() -> sqlParts.joinToString("\n\n")
            else -> searchUiConfigRepository.getValue("search2.noSearchSqlPlaceholder") ?: "-- Enable Vector or BM25"
        }
    }

    data class HybridSearchResult(val results: List<HybridSearchResultDto>, val generatedSql: String, val queryExplanation: String)
    data class HybridSearchResultDto(
        val id: Long,
        val title: String,
        val contentSnippet: String,
        val similarityPercent: Double,
        val totalScore: Double,
    )
}
