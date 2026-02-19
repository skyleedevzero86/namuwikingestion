package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import com.sleekydz86.namuwikingestion.infrastructure.persistence.SearchUiConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HybridSearchServiceTest {

    private val embeddingClient: EmbeddingClient = mock()
    private val docRepository: NamuwikiDocRepository = mock()
    private val searchUiConfigRepository: SearchUiConfigRepository = mock()

    private val service = HybridSearchService(
        embeddingClient = embeddingClient,
        docRepository = docRepository,
        searchUiConfigRepository = searchUiConfigRepository,
    )

    @Test
    fun `search - 빈 쿼리면 config placeholder로 빈 결과 및 SQL 반환`() {
        whenever(searchUiConfigRepository.getValue("search2.emptySqlPlaceholder")).thenReturn("-- empty")
        whenever(searchUiConfigRepository.getValue("search2.emptyExplanationPlaceholder")).thenReturn("[]")

        val result = service.search(
            query = "   ",
            vectorMode = "USE",
            enableBm25 = true,
            keywordWeight = 0.5,
            limit = 20,
        )

        assertTrue(result.results.isEmpty())
        assertEquals("-- empty", result.generatedSql)
        assertEquals("[]", result.queryExplanation)
    }

    @Test
    fun `search - 벡터 모드만 사용 시 embedding 호출하고 벡터 검색 결과 반환`() {
        val query = "검색어"
        val vec = FloatArray(384) { 0.1f }
        whenever(embeddingClient.embedBatch(listOf(query))).thenReturn(listOf(vec))
        val row = NamuwikiDocRepository.VectorSearchRow(1L, "제목", "내용", 0.3)
        whenever(docRepository.searchByVector(eq(vec), eq(50))).thenReturn(listOf(row))
        whenever(docRepository.explainVectorSearch(eq(vec), eq(20))).thenReturn("[]")

        val result = service.search(
            query = query,
            vectorMode = "USE",
            enableBm25 = false,
            keywordWeight = 0.0,
            limit = 20,
        )

        assertEquals(1, result.results.size)
        assertEquals(1L, result.results.first().id)
        assertEquals("제목", result.results.first().title)
        verify(embeddingClient).embedBatch(listOf(query))
        verify(docRepository).searchByVector(vec, 50)
    }

    @Test
    fun `search - vectorMode NONE이면 embedding 호출 안 함`() {
        whenever(docRepository.searchByFullText(eq("쿼리"), eq(50), eq("simple"))).thenReturn(emptyList())
        whenever(docRepository.explainFullTextSearch(eq("쿼리"), eq(50), eq("simple"))).thenReturn("[]")
        whenever(docRepository.getFullTextSearchSqlForDisplay(eq(50), eq("simple"), eq("쿼리"))).thenReturn("-- fulltext")

        service.search(
            query = "쿼리",
            vectorMode = "NONE",
            enableBm25 = true,
            keywordWeight = 0.5,
        )

        verify(embeddingClient, never()).embedBatch(any())
    }
}
