package com.sleekydz86.namuwikingestion.infrastructure.persistence


import com.pgvector.PGvector
import com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc
import com.sleekydz86.namuwikingestion.global.config.InsertConfig
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class NamuwikiDocRepository(
    private val jdbc: JdbcTemplate,
    private val insertConfig: InsertConfig,
) {
    private val batchSize: Int
        get() = insertConfig.batchSize

    fun insertBatch(docs: List<NamuwikiDoc>) {
        if (docs.isEmpty()) return
        val sql = """
            INSERT INTO namuwiki_doc (title, content, embedding, namespace, contributors)
            VALUES (?, ?, ?::vector, ?, ?)
        """.trimIndent()
        jdbc.batchUpdate(sql, docs, docs.size) { ps, doc ->
            ps.setString(1, doc.title)
            ps.setString(2, doc.content)
            ps.setObject(3, PGvector(doc.embedding))
            ps.setString(4, doc.namespace)
            ps.setString(5, doc.contributors)
        }
    }

    fun count(): Long = try {
        jdbc.queryForObject("SELECT COUNT(*) FROM namuwiki_doc", Long::class.java) ?: 0L
    } catch (_: Exception) {
        0L
    }

    data class VectorSearchRow(val id: Long, val title: String, val content: String, val distance: Double)
    data class FullTextSearchRow(val id: Long, val title: String, val content: String, val rank: Double)

    private fun vectorSearchSql(limit: Int) = """
        SELECT id, title, content, (embedding <=> ?::vector) AS dist
        FROM namuwiki_doc
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> ?::vector
        LIMIT $limit
    """.trimIndent()

    fun searchByVector(embedding: FloatArray, limit: Int): List<VectorSearchRow> {
        if (embedding.isEmpty()) return emptyList()
        val v = PGvector(embedding)
        return jdbc.query(vectorSearchSql(limit), VECTOR_ROW_MAPPER, v, v)
    }

    fun getVectorSearchSqlForDisplay(limit: Int): String = vectorSearchSql(limit)

    fun explainVectorSearch(embedding: FloatArray, limit: Int): String {
        if (embedding.isEmpty()) return "[]"
        val v = PGvector(embedding)
        val sql = "EXPLAIN (FORMAT JSON)\n" + vectorSearchSql(limit)
        return try {
            val list = jdbc.query(sql, { rs, _ -> rs.getString(1) }, v, v)
            list?.firstOrNull() ?: "[]"
        } catch (_: Exception) {
            "[]"
        }
    }

    private fun fullTextSearchSql(limit: Int, config: String) = """
        SELECT id, title, content,
               ts_rank(to_tsvector(?, title || ' ' || content), plainto_tsquery(?, ?)) AS r
        FROM namuwiki_doc
        WHERE to_tsvector(?, title || ' ' || content) @@ plainto_tsquery(?, ?)
        ORDER BY r DESC
        LIMIT $limit
    """.trimIndent()

    fun searchByFullText(query: String, limit: Int, config: String = "simple"): List<FullTextSearchRow> {
        if (query.isBlank()) return emptyList()
        return try {
            jdbc.query(fullTextSearchSql(limit, config), FULLTEXT_ROW_MAPPER, config, config, query, config, config, query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getFullTextSearchSqlForDisplay(limit: Int, config: String, queryForDisplay: String?): String {
        val escaped = queryForDisplay?.replace("'", "''") ?: "?"
        val sql = fullTextSearchSql(limit, config)
        val values = listOf(config, config, "'$escaped'", config, config, "'$escaped'")
        val parts = sql.split("?")
        return parts.foldIndexed(StringBuilder()) { i, acc, p ->
            acc.append(p).append(values.getOrNull(i) ?: "")
        }.toString()
    }

    fun explainFullTextSearch(query: String, limit: Int, config: String = "simple"): String {
        if (query.isBlank()) return "[]"
        val sql = "EXPLAIN (FORMAT JSON)\n" + fullTextSearchSql(limit, config)
        return try {
            val list = jdbc.query(sql, { rs, _ -> rs.getString(1) }, config, config, query, config, config, query)
            list?.firstOrNull() ?: "[]"
        } catch (_: Exception) {
            "[]"
        }
    }

    companion object {
        private val VECTOR_ROW_MAPPER = RowMapper<VectorSearchRow> { rs: ResultSet, _: Int ->
            VectorSearchRow(
                id = rs.getLong("id"),
                title = rs.getString("title") ?: "",
                content = rs.getString("content") ?: "",
                distance = rs.getDouble("dist"),
            )
        }
        private val FULLTEXT_ROW_MAPPER = RowMapper<FullTextSearchRow> { rs: ResultSet, _: Int ->
            FullTextSearchRow(
                id = rs.getLong("id"),
                title = rs.getString("title") ?: "",
                content = rs.getString("content") ?: "",
                rank = rs.getDouble("r"),
            )
        }
    }
}
