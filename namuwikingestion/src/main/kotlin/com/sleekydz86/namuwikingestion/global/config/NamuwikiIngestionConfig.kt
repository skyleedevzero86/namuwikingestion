package com.sleekydz86.namuwikingestion.global.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EmbeddingConfig::class, DatasetConfig::class, InsertConfig::class)
class NamuwikiIngestionConfig {}

@ConfigurationProperties(prefix = "namuwiki.dataset")
data class DatasetConfig(
    val hfDataset: String = "heegyu/namuwiki",
    val split: String = "train",
    val limit: Int = 0,
    val localParquetPath: String? = null,
)

@ConfigurationProperties(prefix = "namuwiki.insert")
data class InsertConfig(
    val batchSize: Int = 100,
)
