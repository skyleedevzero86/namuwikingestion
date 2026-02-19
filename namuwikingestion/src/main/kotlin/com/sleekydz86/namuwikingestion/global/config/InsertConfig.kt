package com.sleekydz86.namuwikingestion.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "namuwiki.insert")
data class InsertConfig(
    val batchSize: Int = 100,
)
