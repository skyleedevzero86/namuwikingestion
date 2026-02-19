package com.sleekydz86.namuwikingestion.presentation

import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@RestController
@Hidden
class EmbeddingOpenApiProxyController(
    private val embeddingConfig: EmbeddingConfig,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @GetMapping("/api-docs/embedding-server.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun proxyEmbeddingOpenApi(): ResponseEntity<String> {
        val baseUrl = embeddingBaseUrl() ?: run {
            logger.warn { "임베딩 endpoint-url 미설정, 빈 OpenAPI 스펙 반환" }
            return ResponseEntity.ok("""{"openapi":"3.0.0","info":{"title":"임베딩 API","description":"임베딩 서버에 연결되지 않음"},"paths":{}}""")
        }
        val url = "$baseUrl/openapi.json"
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn { "임베딩 OpenAPI 조회 실패: $url, code=${response.code}" }
                    return ResponseEntity.status(response.code).body("{}")
                }
                val body = response.body?.string() ?: "{}"
                ResponseEntity.ok().body(body)
            }
        } catch (e: Exception) {
            logger.warn(e) { "임베딩 OpenAPI 프록시 실패: $url" }
            ResponseEntity.ok().body("""{"openapi":"3.0.0","info":{"title":"임베딩 API","description":"임베딩 서버 연결 실패"},"paths":{}}""")
        }
    }

    private fun embeddingBaseUrl(): String? {
        val url = embeddingConfig.endpointUrl.trim()
        if (url.isBlank()) return null
        val uri = URI.create(url)
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }
}
