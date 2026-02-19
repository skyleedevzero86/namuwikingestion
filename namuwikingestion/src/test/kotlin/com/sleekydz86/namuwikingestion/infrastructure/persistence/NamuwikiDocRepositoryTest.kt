package com.sleekydz86.namuwikingestion.infrastructure.persistence

import com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc
import com.sleekydz86.namuwikingestion.global.config.InsertConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.never
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class NamuwikiDocRepositoryTest {

    private val jdbc: JdbcTemplate = mock()
    private val insertConfig = InsertConfig(batchSize = 100)
    private val repo = NamuwikiDocRepository(jdbc, insertConfig)

    @Test
    fun `insertBatch - 빈 리스트면 batchUpdate 호출 안 함`() {
        repo.insertBatch(emptyList())
        verify(jdbc, never()).batchUpdate(any(), any(), any(), any())
    }

    @Test
    fun `insertBatch - 문서 1건이면 batchUpdate 한 번 호출`() {
        val doc = NamuwikiDoc(
            title = "제목",
            content = "내용",
            embedding = FloatArray(384) { 0.1f },
            namespace = "0",
            contributors = "user",
        )
        repo.insertBatch(listOf(doc))
        verify(jdbc).batchUpdate(
            any(),
            eq(listOf(doc)),
            eq(1),
            any(),
        )
    }

    @Test
    fun `searchByVector - 빈 벡터면 빈 리스트 반환, jdbc query 호출 안 함`() {
        val result = repo.searchByVector(FloatArray(0), 10)
        assertTrue(result.isEmpty())
        verify(jdbc, never()).query(any(), any<RowMapper<*>>(), any())
    }

    @Test
    fun `count - jdbc가 반환한 값 그대로 반환`() {
        whenever(jdbc.queryForObject(eq("SELECT COUNT(*) FROM namuwiki_doc"), eq(Long::class.java))).thenReturn(42L)
        assertEquals(42L, repo.count())
    }

    @Test
    fun `count - 예외 시 0 반환`() {
        whenever(jdbc.queryForObject(any<String>(), eq(Long::class.java))).thenThrow(RuntimeException("DB 오류"))
        assertEquals(0L, repo.count())
    }
}
