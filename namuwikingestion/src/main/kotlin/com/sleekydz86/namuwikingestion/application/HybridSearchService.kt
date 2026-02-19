package com.sleekydz86.namuwikingestion.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import com.sleekydz86.namuwikingestion.infrastructure.persistence.SearchUiConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}
private const val MAX_QUERY_LENGTH_FOR_EMBED = 512

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
        val queryForEmbed = if (q.length > MAX_QUERY_LENGTH_FOR_EMBED) q.take(MAX_QUERY_LENGTH_FOR_EMBED) else q
        val queryVector = if (useVector) embeddingClient.embedBatch(listOf(queryForEmbed)).singleOrNull() else null

        val semanticWeight = 1.0 - keywordWeight.coerceIn(0.0, 1.0)
        val kwWeight = keywordWeight.coerceIn(0.0, 1.0)

        val useUnifiedPath = useVector && enableBm25 && queryVector != null

        if (useUnifiedPath) {
            val (unifiedRows, queryExplanation, generatedSql) = runBlocking {
                val rowsDeferred = async(Dispatchers.IO) {
                    docRepository.searchByUnifiedHybrid(queryVector!!, q, limit, semanticWeight, kwWeight)
                }
                val explainDeferred = async(Dispatchers.IO) {
                    try {
                        docRepository.explainUnifiedHybridSearch(queryVector!!, q, limit, semanticWeight, kwWeight)
                    } catch (e: Exception) {
                        logger.warn(e) { "explainUnifiedHybridSearch 실패, []" }
                        "[]"
                    }
                }
                val rows = rowsDeferred.await()
                val explain = explainDeferred.await()
                val sql = docRepository.getUnifiedHybridSqlForDisplay(q, limit, semanticWeight, kwWeight)
                Triple(rows, explain, sql)
            }
            val sorted = unifiedRows.map { row ->
                val simPercent = (row.totalScore.coerceIn(0.0, 1.0) * 100.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
                HybridSearchResultDto(
                    id = row.id,
                    title = row.title,
                    contentSnippet = row.content.take(500).let { if (it.length >= 500) "$it..." else it },
                    similarityPercent = simPercent,
                    totalScore = row.totalScore,
                )
            }
            return HybridSearchResult(sorted, generatedSql, queryExplanation)
        }

        val (vectorExplainJson, vectorRows, textRows, fulltextExplainJson) = runBlocking {
            val explainVec = async(Dispatchers.IO) {
                if (queryVector == null) "[]"
                else try {
                    docRepository.explainVectorSearch(queryVector, vectorLimit)
                } catch (e: Exception) {
                    logger.warn(e) { "explainVectorSearch 실패, []로 대체" }
                    "[]"
                }
            }
            val vecRows = async(Dispatchers.IO) {
                if (queryVector == null) emptyList()
                else docRepository.searchByVector(queryVector, 50)
            }
            val txtRows = async(Dispatchers.IO) {
                if (!enableBm25) emptyList()
                else try {
                    docRepository.searchByFullText(q, 50)
                } catch (e: Exception) {
                    logger.warn(e) { "searchByFullText 실패, 빈 목록" }
                    emptyList()
                }
            }
            val ftExplain = async(Dispatchers.IO) {
                if (!enableBm25) "[]"
                else try {
                    docRepository.explainFullTextSearch(q, 50)
                } catch (e: Exception) {
                    logger.warn(e) { "explainFullTextSearch 실패, []" }
                    "[]"
                }
            }
            Quadruple(explainVec.await(), vecRows.await(), txtRows.await(), ftExplain.await())
        }

        val queryExplanation = mergeExplainJson(vectorExplainJson, fulltextExplainJson)
        val generatedSql = buildGeneratedSqlFromRepository(q, useVector, enableBm25)

        val scoresById = mutableMapOf<Long, Pair<Double, HybridSearchDocInfo>>()

        vectorRows.forEach { row ->
            val sim = (2.0 - row.distance.coerceIn(0.0, 2.0)) / 2.0
            val score = semanticWeight * sim
            scoresById[row.id] = (scoresById[row.id]?.first?.plus(score) ?: score) to HybridSearchDocInfo(row.id, row.title, row.content, row.distance)
        }

        textRows.forEach { row ->
            val rank = row.rank.coerceAtLeast(0.0)
            val normRank = 1.0 / (60.0 + rank)
            val score = kwWeight * normRank * 61.0
            val current = scoresById[row.id]
            val docInfo = current?.second ?: HybridSearchDocInfo(row.id, row.title, row.content, 1.0)
            scoresById[row.id] = ((current?.first ?: 0.0) + score) to docInfo
        }

        val sorted = scoresById.entries
            .sortedByDescending { it.value.first }
            .take(limit)
            .map { (_, pair) ->
                val (totalScore, doc) = pair
                val rawSim = (2.0 - doc.distance.coerceIn(0.0, 2.0)) / 2.0 * 100.0
                val similarityPercent = when { rawSim.isNaN() || rawSim.isInfinite() -> 0.0; else -> rawSim.coerceIn(0.0, 100.0) }
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
        } catch (e: Exception) {
            logger.warn(e) { "mergeExplainJson 실패, 원본 문자열 이어붙임" }
            "[$vectorExplain, $fulltextExplain]"
        }
    }

    private fun buildGeneratedSqlFromRepository(query: String, useVector: Boolean, useBm25: Boolean): String {
        val sqlParts = mutableListOf<String>()
        if (useVector) {
            sqlParts.add("-- 벡터 (embedding-server)\n" + docRepository.getVectorSearchSqlForDisplay(50))
        }
        if (useBm25) {
            sqlParts.add("-- 전문 검색 (BM25)\n" + docRepository.getFullTextSearchSqlForDisplay(50, null, query))
        }
        return when {
            sqlParts.isNotEmpty() -> sqlParts.joinToString("\n\n")
            else -> searchUiConfigRepository.getValue("search2.noSearchSqlPlaceholder") ?: "벡터 또는 BM25 사용"
        }
    }

}
