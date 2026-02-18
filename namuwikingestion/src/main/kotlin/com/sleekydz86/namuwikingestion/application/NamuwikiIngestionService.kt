package com.sleekydz86.namuwikingestion.application


import com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc
import com.sleekydz86.namuwikingestion.dataclass.NamuwikiRow
import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import com.sleekydz86.namuwikingestion.global.config.InsertConfig
import com.sleekydz86.namuwikingestion.infrastructure.huggingface.HfDatasetService
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class NamuwikiIngestionService(
    private val hfDataset: HfDatasetService,
    private val embeddingClient: EmbeddingClient,
    private val docRepository: NamuwikiDocRepository,
    private val progressHolder: IngestionProgressHolder,
    private val embeddingConfig: EmbeddingConfig,
    private val insertConfig: InsertConfig,
) {

    fun runIngestion(): IngestionResult {
        progressHolder.start()
        return try {
            val result = processPipeline()
            progressHolder.done(result.totalRowsRead, result.totalInserted)
            logger.info { "수집 완료: ${result.totalRowsRead}건 읽음, ${result.totalInserted}건 저장" }
            result
        } catch (e: Exception) {
            progressHolder.error(e.message ?: e.toString())
            logger.error(e) { "수집 실패" }
            throw e
        }
    }

    private fun processPipeline(): IngestionResult {
        var totalRows = 0
        var inserted = 0
        val rowBuffer = mutableListOf<NamuwikiRow>()
        val docBuffer = mutableListOf<NamuwikiDoc>()
        val embedBatchSize = embeddingConfig.batchSize
        val insertBatchSize = insertConfig.batchSize

        for (row in hfDataset.streamRows()) {
            totalRows++
            rowBuffer.add(row)
            if (rowBuffer.size >= embedBatchSize) {
                docBuffer.addAll(embedAndConvert(rowBuffer))
                rowBuffer.clear()
                flushDocBuffer(docBuffer, insertBatchSize) { inserted += it }
                progressHolder.update(totalRows, inserted)
            }
        }
        if (rowBuffer.isNotEmpty()) {
            docBuffer.addAll(embedAndConvert(rowBuffer))
        }
        while (docBuffer.isNotEmpty()) {
            inserted += flushDocBuffer(docBuffer, insertBatchSize) { }
            progressHolder.update(totalRows, inserted)
        }
        return IngestionResult(totalRowsRead = totalRows, totalInserted = inserted)
    }

    private fun flushDocBuffer(
        docBuffer: MutableList<NamuwikiDoc>,
        batchSize: Int,
        onFlushed: (Int) -> Unit,
    ): Int {
        val batch = docBuffer.take(batchSize)
        if (batch.isEmpty()) return 0
        docRepository.insertBatch(batch)
        repeat(batch.size) { docBuffer.removeAt(0) }
        onFlushed(batch.size)
        return batch.size
    }

    private fun embedAndConvert(rows: List<NamuwikiRow>): List<NamuwikiDoc> {
        val texts = rows.map { it.text }
        val embeddings = embeddingClient.embedBatch(texts)
        return rows.zip(embeddings) { row, vec ->
            NamuwikiDoc(
                title = row.title,
                content = row.text,
                embedding = vec,
                namespace = row.namespace,
                contributors = row.contributors,
            )
        }
    }

    data class IngestionResult(val totalRowsRead: Int, val totalInserted: Int)
}
