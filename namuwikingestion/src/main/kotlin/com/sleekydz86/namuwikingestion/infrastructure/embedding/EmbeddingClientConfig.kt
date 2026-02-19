package com.sleekydz86.namuwikingestion.infrastructure.embedding

import com.sleekydz86.namuwikingestion.domain.port.EmbeddingClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class EmbeddingClientConfig {

    @Bean
    @Primary
    fun embeddingClient(httpEmbeddingClient: HttpEmbeddingClient): EmbeddingClient =
        CachingEmbeddingClient(httpEmbeddingClient, maxSize = 200)
}
