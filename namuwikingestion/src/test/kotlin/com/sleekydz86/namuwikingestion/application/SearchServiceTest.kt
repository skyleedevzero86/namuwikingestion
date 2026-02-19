package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import com.sleekydz86.namuwikingestion.infrastructure.persistence.VectorSearchRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchServiceTest {

    private val embeddingClient: EmbeddingClient = mock()
    private val docRepository: NamuwikiDocRepository = mock()
    private val minSimilarityPercent = 55.0
    private val searchService = SearchService(embeddingClient, docRepository, minSimilarityPercent)

    @Test
    fun `search - 빈 문자열이면 빈 리스트 반환, embedding 호출 안 함`() {
        val result = searchService.search("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search - 공백만 있으면 빈 리스트 반환`() {
        val result = searchService.search("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search - embedding이 빈 리스트면 빈 결과`() {
        whenever(embeddingClient.embedBatch(listOf("쿼리"))).thenReturn(emptyList())
        val result = searchService.search("쿼리")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search - 검색 결과가 minSimilarityPercent 미만이면 제외`() {
        val query = "테스트"
        val vec = FloatArray(384) { 0.1f }
        whenever(embeddingClient.embedBatch(listOf(query))).thenReturn(listOf(vec))
        val lowSimilarityRow = VectorSearchRow(
            id = 1L,
            title = "제목",
            content = "내용",
            distance = 1.5,
        )
        whenever(docRepository.searchByVector(eq(vec), eq(20))).thenReturn(listOf(lowSimilarityRow))

        val result = searchService.search(query, limit = 20)

        assertTrue(result.isEmpty() || result.all { it.similarityPercent >= minSimilarityPercent })
    }

    @Test
    fun `search - 정상 쿼리 시 embedding 한 번, searchByVector 한 번 호출`() {
        val query = "나무"
        val vec = FloatArray(384) { 0.1f }
        whenever(embeddingClient.embedBatch(listOf(query))).thenReturn(listOf(vec))
        val row = VectorSearchRow(
            id = 1L,
            title = "나무위키",
            content = "나무위키 내용",
            distance = 0.2,
        )
        whenever(docRepository.searchByVector(eq(vec), eq(20))).thenReturn(listOf(row))

        val result = searchService.search(query, limit = 20)

        verify(embeddingClient).embedBatch(listOf(query))
        verify(docRepository).searchByVector(vec, 20)
        assertTrue(result.isNotEmpty())
        assertEquals(1L, result.first().id)
        assertEquals("나무위키", result.first().title)
        assertTrue(result.first().similarityPercent >= minSimilarityPercent)
    }

    @Test
    fun `search - limit 전달 시 repository에 동일 limit 전달`() {
        val query = "검색"
        val vec = FloatArray(384) { 0.1f }
        whenever(embeddingClient.embedBatch(listOf(query))).thenReturn(listOf(vec))
        whenever(docRepository.searchByVector(any(), eq(50))).thenReturn(emptyList())

        searchService.search(query, limit = 50)

        verify(docRepository).searchByVector(vec, 50)
    }
}
