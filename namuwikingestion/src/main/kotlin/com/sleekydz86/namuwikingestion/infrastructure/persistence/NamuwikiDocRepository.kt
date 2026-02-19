package com.sleekydz86.namuwikingestion.infrastructure.persistence

import com.pgvector.PGvector
import com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc
import com.sleekydz86.namuwikingestion.global.config.InsertConfig
import mu.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet

private val logger = KotlinLogging.logger {}

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
    } catch (e: Exception) {
        logger.warn(e) { "count() 실패, 0 반환" }
        0L
    }

    data class VectorSearchRow(val id: Long, val title: String, val content: String, val distance: Double)
    data class FullTextSearchRow(val id: Long, val title: String, val content: String, val rank: Double)
    data class UnifiedHybridRow(val id: Long, val title: String, val content: String, val totalScore: Double)

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
        } catch (e: Exception) {
            logger.warn(e) { "explainVectorSearch 실패, [] 반환" }
            "[]"
        }
    }

    private fun fullTextSearchSql(limit: Int, config: String) = """
        SELECT id, title, content,
               ts_rank(to_tsvector(?::regconfig, title || ' ' || content), plainto_tsquery(?::regconfig, ?)) AS r
        FROM namuwiki_doc
        WHERE to_tsvector(?::regconfig, title || ' ' || content) @@ plainto_tsquery(?::regconfig, ?)
        ORDER BY r DESC
        LIMIT $limit
    """.trimIndent()

    fun searchByFullText(query: String, limit: Int, config: String = "simple"): List<FullTextSearchRow> {
        if (query.isBlank()) return emptyList()
        return try {
            jdbc.query(fullTextSearchSql(limit, config), FULLTEXT_ROW_MAPPER, config, config, query, config, config, query)
        } catch (e: Exception) {
            logger.warn(e) { "searchByFullText 실패, query=$query, 빈 목록 반환" }
            emptyList()
        }
    }

    fun getFullTextSearchSqlForDisplay(limit: Int, config: String, queryForDisplay: String?): String {
        val escaped = queryForDisplay?.replace("'", "''") ?: "?"
        val sql = fullTextSearchSql(limit, config)
        val configQuoted = "'$config'"
        val values = listOf(configQuoted, configQuoted, "'$escaped'", configQuoted, configQuoted, "'$escaped'")
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
        } catch (e: Exception) {
            logger.warn(e) { "explainFullTextSearch 실패, query=$query, [] 반환" }
            "[]"
        }
    }

    private val tsConfig = "simple"

    fun getUnifiedHybridSqlForDisplay(query: String, limit: Int, semanticWeight: Double, keywordWeight: Double): String {
        val escaped = query.replace("'", "''")
        return """
WITH vector_results AS (
  SELECT id, (embedding <=> ?::vector) AS dist
  FROM namuwiki_doc
  WHERE embedding IS NOT NULL
  ORDER BY embedding <=> ?::vector
  LIMIT 50
),
text_results AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank(to_tsvector('$tsConfig', title || ' ' || content), plainto_tsquery('$tsConfig', '$escaped')) DESC) AS r
  FROM namuwiki_doc
  WHERE to_tsvector('$tsConfig', title || ' ' || content) @@ plainto_tsquery('$tsConfig', '$escaped')
  LIMIT 50
),
fusion AS (
  SELECT id, SUM(score) AS total_score FROM (
    SELECT id, $semanticWeight * (1.0 - (dist / 2.0)) AS score FROM vector_results
    UNION ALL
    SELECT id, (1.0 / (60 + r)) * 61.0 * $keywordWeight AS score FROM text_results
  ) t GROUP BY id
)
SELECT nd.id, nd.title, nd.content
FROM namuwiki_doc nd
JOIN fusion f ON nd.id = f.id
ORDER BY f.total_score DESC
LIMIT $limit
        """.trimIndent()
    }

    private fun unifiedHybridSql(limit: Int): String = """
WITH vector_results AS (
  SELECT id, (embedding <=> ?::vector) AS dist
  FROM namuwiki_doc
  WHERE embedding IS NOT NULL
  ORDER BY embedding <=> ?::vector
  LIMIT 50
),
text_results AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank(to_tsvector(?::regconfig, title || ' ' || content), plainto_tsquery(?::regconfig, ?)) DESC) AS r
  FROM namuwiki_doc
  WHERE to_tsvector(?::regconfig, title || ' ' || content) @@ plainto_tsquery(?::regconfig, ?)
  LIMIT 50
),
fusion AS (
  SELECT id, SUM(score) AS total_score FROM (
    SELECT id, ? * (1.0 - (dist / 2.0)) AS score FROM vector_results
    UNION ALL
    SELECT id, (1.0 / (60 + r)) * 61.0 * ? AS score FROM text_results
  ) t GROUP BY id
)
SELECT nd.id, nd.title, nd.content, f.total_score
FROM namuwiki_doc nd
JOIN fusion f ON nd.id = f.id
ORDER BY f.total_score DESC
LIMIT ?
        """.trimIndent()

    fun searchByUnifiedHybrid(
        embedding: FloatArray,
        query: String,
        limit: Int,
        semanticWeight: Double,
        keywordWeight: Double,
    ): List<UnifiedHybridRow> {
        if (embedding.isEmpty() || query.isBlank()) return emptyList()
        val v = PGvector(embedding)
        return try {
            jdbc.query(
                unifiedHybridSql(limit),
                RowMapper { rs: ResultSet, _: Int ->
                    UnifiedHybridRow(
                        id = rs.getLong("id"),
                        title = rs.getString("title") ?: "",
                        content = rs.getString("content") ?: "",
                        totalScore = rs.getDouble("total_score"),
                    )
                },
                v, v,
                tsConfig, tsConfig, query, tsConfig, tsConfig, query,
                semanticWeight, keywordWeight,
                limit,
            ) ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "searchByUnifiedHybrid 실패, 빈 목록" }
            emptyList()
        }
    }

    fun explainUnifiedHybridSearch(
        embedding: FloatArray,
        query: String,
        limit: Int,
        semanticWeight: Double,
        keywordWeight: Double,
    ): String {
        if (embedding.isEmpty() && query.isBlank()) return "[]"
        val v = PGvector(embedding)
        val sql = "EXPLAIN (FORMAT JSON)\n" + unifiedHybridSql(limit)
        return try {
            val list = jdbc.query(
                sql,
                { rs, _ -> rs.getString(1) },
                v, v,
                tsConfig, tsConfig, query, tsConfig, tsConfig, query,
                semanticWeight, keywordWeight,
                limit,
            )
            list?.firstOrNull() ?: "[]"
        } catch (e: Exception) {
            logger.warn(e) { "explainUnifiedHybridSearch 실패, [] 반환" }
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
