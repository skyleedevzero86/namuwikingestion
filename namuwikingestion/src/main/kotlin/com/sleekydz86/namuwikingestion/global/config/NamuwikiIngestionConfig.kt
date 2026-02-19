package com.sleekydz86.namuwikingestion.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.properties.EnableConfigurationProperties

@Configuration
@EnableConfigurationProperties(EmbeddingConfig::class, DatasetConfig::class, InsertConfig::class, FullTextSearchConfig::class)
class NamuwikiIngestionConfig
