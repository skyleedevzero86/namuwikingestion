package com.sleekydz86.namuwikingestion.infrastructure.persistence


import com.pgvector.PGvector
import com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc
import com.sleekydz86.namuwikingestion.global.config.InsertConfig
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
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
}
