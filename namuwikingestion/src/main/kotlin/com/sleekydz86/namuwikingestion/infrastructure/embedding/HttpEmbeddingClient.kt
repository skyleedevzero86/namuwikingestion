package com.sleekydz86.namuwikingestion.infrastructure.embedding


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import org.springframework.stereotype.Component
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Component
class HttpEmbeddingClient(
    private val config: EmbeddingConfig,
) : EmbeddingClient {
    private val objectMapper = jacksonObjectMapper()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override fun embedBatch(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        if (config.endpointUrl.isBlank()) {
            throw IllegalStateException("namuwiki.embedding.endpoint-url 설정되지 않음")
        }
        val truncated = texts.map { t ->
            if (config.maxTextLength > 0 && t.length > config.maxTextLength) t.take(config.maxTextLength) else t
        }
        val requestBody = mapOf("texts" to truncated)
        val json = objectMapper.writeValueAsString(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(config.endpointUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw RuntimeException("임베딩 API 실패: ${response.code} - $errBody")
        }
        val responseBody = response.body?.string() ?: ""
        val parsed = objectMapper.readValue<EmbeddingResponse>(responseBody)
        if (parsed.embeddings.size != texts.size) {
            throw RuntimeException("임베딩 API: 텍스트 ${texts.size}건에 대해 벡터 ${parsed.embeddings.size}개 반환 (불일치)")
        }
        return parsed.embeddings.map { list -> list.map { d -> d.toFloat() }.toFloatArray() }
    }

    private data class EmbeddingResponse(val embeddings: List<List<Double>>)
}
