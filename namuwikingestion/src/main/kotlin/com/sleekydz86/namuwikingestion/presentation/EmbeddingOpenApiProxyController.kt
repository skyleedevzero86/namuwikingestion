package com.sleekydz86.namuwikingestion.presentation

import com.sleekydz86.namuwikingestion.global.config.EmbeddingConfig
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

private val logger = KotlinLogging.logger {}

@RestController
class EmbeddingOpenApiProxyController(
    private val embeddingConfig: EmbeddingConfig,
) {

    @GetMapping("/embedding-openapi.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getEmbeddingOpenApi(): ResponseEntity<String> {
        if (embeddingConfig.endpointUrl.isBlank()) {
            return ResponseEntity.ok().body("""{"openapi":"3.0.0","info":{"title":"나무위키 임베딩 API","description":"임베딩 서버 URL이 설정되지 않았습니다."},"paths":{}}""")
        }
        val baseUrl = embeddingConfig.endpointUrl.trimEnd('/').substringBeforeLast("/")
        val openApiUrl = "$baseUrl/openapi.json"
        return try {
            val client = java.net.http.HttpClient.newBuilder().build()
            val request = java.net.http.HttpRequest.newBuilder(URI.create(openApiUrl)).GET().build()
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.body())
            } else {
                logger.warn { "임베딩 OpenAPI 조회 실패: $openApiUrl, status=${response.statusCode()}" }
                val status = response.statusCode()
                val fallback = """{"openapi":"3.0.0","info":{"title":"나무위키 임베딩 API","description":"임베딩 서버 OpenAPI 조회 실패 (HTTP $status)"},"paths":{}}"""
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fallback)
            }
        } catch (e: Exception) {
            logger.warn(e) { "임베딩 OpenAPI 프록시 실패: $openApiUrl" }
            ResponseEntity.ok().body("""{"openapi":"3.0.0","info":{"title":"나무위키 임베딩 API","description":"임베딩 서버에 연결할 수 없습니다."},"paths":{}}""")
        }
    }
}
