package com.sleekydz86.namuwikingestion.infrastructure.persistence

import com.pgvector.PGvector
import com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc
import com.sleekydz86.namuwikingestion.global.config.FullTextSearchConfig
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
    private val fullTextSearchConfig: FullTextSearchConfig,
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

    private fun fullTextSearchSql(limit: Int, config: String) =
        if (fullTextSearchConfig.useTsvContent()) {
            """
                SELECT id, title, content,
                       ts_rank_cd(tsv_content, plainto_tsquery(?::regconfig, ?), 1) AS r
                FROM namuwiki_doc
                WHERE tsv_content IS NOT NULL AND tsv_content @@ plainto_tsquery(?::regconfig, ?)
                ORDER BY r DESC
                LIMIT $limit
            """.trimIndent()
        } else {
            """
                SELECT id, title, content,
                       ts_rank(to_tsvector(?::regconfig, title || ' ' || content), plainto_tsquery(?::regconfig, ?)) AS r
                FROM namuwiki_doc
                WHERE to_tsvector(?::regconfig, title || ' ' || content) @@ plainto_tsquery(?::regconfig, ?)
                ORDER BY r DESC
                LIMIT $limit
            """.trimIndent()
        }

    fun searchByFullText(query: String, limit: Int, config: String? = null): List<FullTextSearchRow> {
        if (query.isBlank()) return emptyList()
        val regconfig = config ?: fullTextSearchConfig.fulltextRegconfig
        return try {
            if (fullTextSearchConfig.useTsvContent()) {
                jdbc.query(fullTextSearchSql(limit, regconfig), FULLTEXT_ROW_MAPPER, regconfig, query, regconfig, query)
            } else {
                jdbc.query(fullTextSearchSql(limit, regconfig), FULLTEXT_ROW_MAPPER, regconfig, query, regconfig, query, regconfig, query)
            }
        } catch (e: Exception) {
            logger.warn(e) { "searchByFullText 실패, query=$query, 빈 목록 반환" }
            emptyList()
        }
    }

    fun getFullTextSearchSqlForDisplay(limit: Int, config: String?, queryForDisplay: String?): String {
        val regconfig = config ?: fullTextSearchConfig.fulltextRegconfig
        val escaped = queryForDisplay?.replace("'", "''") ?: "?"
        val sql = fullTextSearchSql(limit, regconfig)
        val configQuoted = "'$regconfig'"
        val values = if (fullTextSearchConfig.useTsvContent()) {
            listOf(configQuoted, "'$escaped'", configQuoted, "'$escaped'")
        } else {
            listOf(configQuoted, configQuoted, "'$escaped'", configQuoted, configQuoted, "'$escaped'")
        }
        val parts = sql.split("?")
        return parts.foldIndexed(StringBuilder()) { i, acc, p ->
            acc.append(p).append(values.getOrNull(i) ?: "")
        }.toString()
    }

    fun explainFullTextSearch(query: String, limit: Int, config: String? = null): String {
        if (query.isBlank()) return "[]"
        val regconfig = config ?: fullTextSearchConfig.fulltextRegconfig
        val sql = "EXPLAIN (FORMAT JSON)\n" + fullTextSearchSql(limit, regconfig)
        return try {
            val list = if (fullTextSearchConfig.useTsvContent()) {
                jdbc.query(sql, { rs, _ -> rs.getString(1) }, regconfig, query, regconfig, query)
            } else {
                jdbc.query(sql, { rs, _ -> rs.getString(1) }, regconfig, query, regconfig, query, regconfig, query)
            }
            list?.firstOrNull() ?: "[]"
        } catch (e: Exception) {
            logger.warn(e) { "explainFullTextSearch 실패, query=$query, [] 반환" }
            "[]"
        }
    }

    private val rrfK: Int get() = fullTextSearchConfig.rrfK

    fun getUnifiedHybridSqlForDisplay(query: String, limit: Int, semanticWeight: Double, keywordWeight: Double): String {
        val escaped = query.replace("'", "''")
        val cfg = fullTextSearchConfig.fulltextRegconfig
        val textCte = if (fullTextSearchConfig.useTsvContent()) {
            """
  SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(tsv_content, plainto_tsquery('$cfg', '$escaped'), 1) DESC) AS r
  FROM namuwiki_doc
  WHERE tsv_content IS NOT NULL AND tsv_content @@ plainto_tsquery('$cfg', '$escaped')
  LIMIT 60
            """.trimIndent()
        } else {
            """
  SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank(to_tsvector('$cfg', title || ' ' || content), plainto_tsquery('$cfg', '$escaped')) DESC) AS r
  FROM namuwiki_doc
  WHERE to_tsvector('$cfg', title || ' ' || content) @@ plainto_tsquery('$cfg', '$escaped')
  LIMIT 60
            """.trimIndent()
        }
        return """
WITH vector_results AS (
  SELECT id, (embedding <=> ?::vector) AS dist
  FROM namuwiki_doc
  WHERE embedding IS NOT NULL
  ORDER BY embedding <=> ?::vector
  LIMIT 60
),
text_results AS (
$textCte
),
fusion AS (
  SELECT id, SUM(score) AS total_score FROM (
    SELECT id, $semanticWeight * (1.0 - (dist / 2.0)) AS score FROM vector_results
    UNION ALL
    SELECT id, (1.0 / ($rrfK + r)) * ${rrfK + 1} * $keywordWeight AS score FROM text_results
  ) t GROUP BY id
)
SELECT nd.id, nd.title, nd.content
FROM namuwiki_doc nd
JOIN fusion f ON nd.id = f.id
ORDER BY f.total_score DESC
LIMIT $limit
        """.trimIndent()
    }

    private fun unifiedHybridSql(limit: Int): String {
        val k = fullTextSearchConfig.rrfK
        return if (fullTextSearchConfig.useTsvContent()) {
            """
WITH vector_results AS (
  SELECT id, (embedding <=> ?::vector) AS dist
  FROM namuwiki_doc
  WHERE embedding IS NOT NULL
  ORDER BY embedding <=> ?::vector
  LIMIT 60
),
text_results AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(tsv_content, plainto_tsquery(?::regconfig, ?), 1) DESC) AS r
  FROM namuwiki_doc
  WHERE tsv_content IS NOT NULL AND tsv_content @@ plainto_tsquery(?::regconfig, ?)
  LIMIT 60
),
fusion AS (
  SELECT id, SUM(score) AS total_score FROM (
    SELECT id, ? * (1.0 - (dist / 2.0)) AS score FROM vector_results
    UNION ALL
    SELECT id, (1.0 / ($k + r)) * ${k + 1} * ? AS score FROM text_results
  ) t GROUP BY id
)
SELECT nd.id, nd.title, nd.content, f.total_score
FROM namuwiki_doc nd
JOIN fusion f ON nd.id = f.id
ORDER BY f.total_score DESC
LIMIT ?
            """.trimIndent()
        } else {
            """
WITH vector_results AS (
  SELECT id, (embedding <=> ?::vector) AS dist
  FROM namuwiki_doc
  WHERE embedding IS NOT NULL
  ORDER BY embedding <=> ?::vector
  LIMIT 60
),
text_results AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank(to_tsvector(?::regconfig, title || ' ' || content), plainto_tsquery(?::regconfig, ?)) DESC) AS r
  FROM namuwiki_doc
  WHERE to_tsvector(?::regconfig, title || ' ' || content) @@ plainto_tsquery(?::regconfig, ?)
  LIMIT 60
),
fusion AS (
  SELECT id, SUM(score) AS total_score FROM (
    SELECT id, ? * (1.0 - (dist / 2.0)) AS score FROM vector_results
    UNION ALL
    SELECT id, (1.0 / ($k + r)) * ${k + 1} * ? AS score FROM text_results
  ) t GROUP BY id
)
SELECT nd.id, nd.title, nd.content, f.total_score
FROM namuwiki_doc nd
JOIN fusion f ON nd.id = f.id
ORDER BY f.total_score DESC
LIMIT ?
            """.trimIndent()
        }
    }

    fun searchByUnifiedHybrid(
        embedding: FloatArray,
        query: String,
        limit: Int,
        semanticWeight: Double,
        keywordWeight: Double,
    ): List<UnifiedHybridRow> {
        if (embedding.isEmpty() || query.isBlank()) return emptyList()
        val v = PGvector(embedding)
        val cfg = fullTextSearchConfig.fulltextRegconfig
        return try {
            if (fullTextSearchConfig.useTsvContent()) {
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
                    cfg, query, cfg, query,
                    semanticWeight, keywordWeight,
                    limit,
                ) ?: emptyList()
            } else {
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
                    cfg, cfg, query, cfg, cfg, query,
                    semanticWeight, keywordWeight,
                    limit,
                ) ?: emptyList()
            }
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
        val cfg = fullTextSearchConfig.fulltextRegconfig
        val sql = "EXPLAIN (FORMAT JSON)\n" + unifiedHybridSql(limit)
        return try {
            val list = if (fullTextSearchConfig.useTsvContent()) {
                jdbc.query(sql, { rs, _ -> rs.getString(1) }, v, v, cfg, query, cfg, query, semanticWeight, keywordWeight, limit)
            } else {
                jdbc.query(sql, { rs, _ -> rs.getString(1) }, v, v, cfg, cfg, query, cfg, cfg, query, semanticWeight, keywordWeight, limit)
            }
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
