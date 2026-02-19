package com.sleekydz86.namuwikingestion.infrastructure.embedding

import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.stereotype.Component
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Component
class EmbeddingHealthCheck(
    private val config: EmbeddingConfig,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    private val objectMapper = jacksonObjectMapper()

    fun check(): EmbeddingHealthResult {
        if (config.endpointUrl.isBlank()) {
            return EmbeddingHealthResult(ok = false, healthUrl = null, model = null)
        }
        val healthUrl = healthUrlFromEndpoint(config.endpointUrl)
        val request = Request.Builder().url(healthUrl).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return EmbeddingHealthResult(ok = false, healthUrl = healthUrl, model = null)
                }
                val body = response.body?.string() ?: ""
                val parsed = objectMapper.readValue<HealthResponse>(body)
                EmbeddingHealthResult(ok = true, healthUrl = healthUrl, model = parsed.model)
            }
        } catch (e: Exception) {
            logger.warn(e) { "임베딩 헬스 체크 실패, healthUrl=$healthUrl" }
            EmbeddingHealthResult(ok = false, healthUrl = healthUrl, model = null)
        }
    }

    private fun healthUrlFromEndpoint(endpointUrl: String): String {
        val uri = URI.create(endpointUrl)
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port/health"
    }

    private data class HealthResponse(val status: String?, val model: String?)
}
