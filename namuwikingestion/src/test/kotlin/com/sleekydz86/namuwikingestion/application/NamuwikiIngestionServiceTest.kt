package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.dataclass.NamuwikiRow
import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import com.sleekydz86.namuwikingestion.global.config.InsertConfig
import com.sleekydz86.namuwikingestion.infrastructure.huggingface.HfDatasetService
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NamuwikiIngestionServiceTest {

    private val hfDataset: HfDatasetService = mock()
    private val embeddingClient: EmbeddingClient = mock()
    private val docRepository: NamuwikiDocRepository = mock()
    private val progressHolder: IngestionProgressHolder = mock()
    private val embeddingConfig = EmbeddingConfig(
        endpointUrl = "http://localhost:8000/embed",
        batchSize = 2,
    )
    private val insertConfig = InsertConfig(batchSize = 2)

    private val service = NamuwikiIngestionService(
        hfDataset = hfDataset,
        embeddingClient = embeddingClient,
        docRepository = docRepository,
        progressHolder = progressHolder,
        embeddingConfig = embeddingConfig,
        insertConfig = insertConfig,
    )

    @Test
    fun `runIngestion - 빈 스트림이면 0건 읽음 0건 저장`() {
        whenever(hfDataset.streamRows()).thenReturn(emptyList<NamuwikiRow>().asSequence())

        val result = service.runIngestion()

        assertEquals(0, result.totalRowsRead)
        assertEquals(0, result.totalInserted)
        verify(progressHolder).start()
        verify(progressHolder).done(0, 0)
    }

    @Test
    fun `runIngestion - 4행 스트림이면 4건 읽음 4건 저장, insertBatch 호출`() {
        val rows = listOf(
            NamuwikiRow("제목1", "내용1"),
            NamuwikiRow("제목2", "내용2"),
            NamuwikiRow("제목3", "내용3"),
            NamuwikiRow("제목4", "내용4"),
        )
        whenever(hfDataset.streamRows()).thenReturn(rows.asSequence())
        val vec = FloatArray(384) { 0.1f }
        whenever(embeddingClient.embedBatch(any())).thenReturn(listOf(vec, vec))

        val result = service.runIngestion()

        assertEquals(4, result.totalRowsRead)
        assertEquals(4, result.totalInserted)

        val insertCaptor = argumentCaptor<List<com.sleekydz86.namuwikingestion.dataclass.NamuwikiDoc>>()
        verify(docRepository, org.mockito.kotlin.atLeastOnce()).insertBatch(insertCaptor.capture())
        val totalInserted = insertCaptor.allValues.sumOf { it.size }
        assertEquals(4, totalInserted)
    }

    @Test
    fun `runIngestion - 예외 발생 시 progressHolder error 호출 후 예외 전파`() {
        whenever(hfDataset.streamRows()).thenThrow(RuntimeException("HF 오류"))

        org.junit.jupiter.api.assertThrows<RuntimeException> {
            service.runIngestion()
        }

        verify(progressHolder).start()
        verify(progressHolder).error("HF 오류")
    }
}
