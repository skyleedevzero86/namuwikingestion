package com.sleekydz86.namuwikingestion.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "namuwiki.dataset")
data class DatasetConfig(
    val hfDataset: String = "heegyu/namuwiki",
    val hfCommit: String? = null,
    val parquetFileName: String? = null,
    val split: String = "train",
    val limit: Int = 0,
    val localParquetPath: String? = null,
    val downloadDir: String? = null,
)
