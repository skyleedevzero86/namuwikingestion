package com.sleekydz86.namuwikingestion.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class SearchUiConfigRepositoryTest {

    private val jdbc: JdbcTemplate = mock()
    private val repo = SearchUiConfigRepository(jdbc)

    @Test
    fun `getValue - 키에 해당하는 값 반환`() {
        whenever(
            jdbc.queryForObject(
                eq("SELECT value FROM search_ui_config WHERE key = ?"),
                eq(String::class.java),
                eq("some.key"),
            ),
        ).thenReturn("some-value")

        val result = repo.getValue("some.key")

        assertEquals("some-value", result)
        verify(jdbc).queryForObject(
            "SELECT value FROM search_ui_config WHERE key = ?",
            String::class.java,
            "some.key",
        )
    }

    @Test
    fun `getValue - 없으면 null`() {
        whenever(
            jdbc.queryForObject(any(), eq(String::class.java), eq("missing")),
        ).thenThrow(RuntimeException("no row"))

        val result = repo.getValue("missing")

        assertNull(result)
    }

    @Test
    fun `getAllByPrefix - prefix로 조회한 key-value 맵 반환`() {
        val rows = listOf(
            "search2.a" to "1",
            "search2.b" to "2",
        )
        whenever(
            jdbc.query(
                eq("SELECT key, value FROM search_ui_config WHERE key LIKE ?"),
                any<RowMapper<Pair<String, String>>>(),
                eq("search2%"),
            ),
        ).thenReturn(rows)

        val result = repo.getAllByPrefix("search2")

        assertEquals(mapOf("search2.a" to "1", "search2.b" to "2"), result)
    }

    @Test
    fun `getAllByPrefix - 예외 시 빈 맵`() {
        whenever(
            jdbc.query(any(), any<RowMapper<Pair<String, String>>>(), any()),
        ).thenThrow(RuntimeException("DB 오류"))

        val result = repo.getAllByPrefix("x")

        assertEquals(emptyMap<String, String>(), result)
    }
}
