package com.sleekydz86.namuwikingestion.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "namuwiki.embedding")
data class EmbeddingConfig(
    val endpointUrl: String = "",
    val apiKey: String = "",
    val batchSize: Int = 32,
    val timeoutSeconds: Long = 60,
    val maxTextLength: Int = 8192,
)
