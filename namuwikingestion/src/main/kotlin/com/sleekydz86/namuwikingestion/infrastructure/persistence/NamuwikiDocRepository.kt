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

    fun searchByVector(embedding: FloatArray, limit: Int): List<VectorSearchRow> {
        if (embedding.isEmpty()) return emptyList()
        val v = PGvector(embedding)
        val sql = """
            SELECT id, title, content, (embedding <=> ?) AS dist
            FROM namuwiki_doc
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?
            LIMIT ?
        """.trimIndent()
        return jdbc.query(sql, VECTOR_ROW_MAPPER, v, v, limit)
    }

    companion object {
        private val VECTOR_ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            VectorSearchRow(
                id = rs.getLong("id"),
                title = rs.getString("title") ?: "",
                content = rs.getString("content") ?: "",
                distance = rs.getDouble("dist"),
            )
        }
    }

    data class VectorSearchRow(val id: Long, val title: String, val content: String, val distance: Double)
}
