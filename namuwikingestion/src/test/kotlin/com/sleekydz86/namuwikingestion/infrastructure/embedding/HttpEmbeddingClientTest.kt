package com.sleekydz86.namuwikingestion.infrastructure.embedding

import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpEmbeddingClientTest {

    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `embedBatch - 빈 리스트면 호출 없이 빈 리스트 반환`() {
        val config = EmbeddingConfig(endpointUrl = "http://localhost:9999/embed")
        val client = HttpEmbeddingClient(config)

        val result = client.embedBatch(emptyList())

        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `embedBatch - endpointUrl 비어 있으면 IllegalStateException`() {
        val config = EmbeddingConfig(endpointUrl = "")
        val client = HttpEmbeddingClient(config)

        assertThrows<IllegalStateException> {
            client.embedBatch(listOf("텍스트"))
        }
    }

    @Test
    fun `embedBatch - 200과 올바른 JSON이면 FloatArray 리스트 반환`() {
        server.start()
        val embeddingsJson = """{"embeddings":[[0.1,0.2,0.3]]}"""
        server.enqueue(
            MockResponse()
                .setBody(embeddingsJson)
                .addHeader("Content-Type", "application/json"),
        )
        val config = EmbeddingConfig(
            endpointUrl = server.url("/embed").toString(),
            timeoutSeconds = 5,
        )
        val client = HttpEmbeddingClient(config)

        val result = client.embedBatch(listOf("hello"))

        assertEquals(1, result.size)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), result[0])
    }

    @Test
    fun `embedBatch - 384차원 벡터 정상 파싱`() {
        server.start()
        val dims = (1..384).map { it * 0.01 }
        val body = """{"embeddings":[[""" + dims.joinToString(",") + """]]}"""
        server.enqueue(
            MockResponse().setBody(body).addHeader("Content-Type", "application/json"),
        )
        val config = EmbeddingConfig(endpointUrl = server.url("/embed").toString())
        val client = HttpEmbeddingClient(config)

        val result = client.embedBatch(listOf("text"))

        assertEquals(1, result.size)
        assertEquals(384, result[0].size)
        assertEquals(0.01f, result[0][0])
        assertEquals(3.84f, result[0][383])
    }

    @Test
    fun `embedBatch - 404 응답이면 RuntimeException`() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val config = EmbeddingConfig(
            endpointUrl = server.url("/embed").toString(),
            timeoutSeconds = 5,
        )
        val client = HttpEmbeddingClient(config)

        assertThrows<RuntimeException> {
            client.embedBatch(listOf("x"))
        }
    }

    @Test
    fun `embedBatch - 텍스트 개수와 embeddings 개수 불일치면 RuntimeException`() {
        server.start()
        server.enqueue(
            MockResponse()
                .setBody("""{"embeddings":[[0.1],[0.2],[0.3]]}""")
                .addHeader("Content-Type", "application/json"),
        )
        val config = EmbeddingConfig(endpointUrl = server.url("/embed").toString())
        val client = HttpEmbeddingClient(config)

        assertThrows<RuntimeException> {
            client.embedBatch(listOf("a", "b"))
        }
    }
}
