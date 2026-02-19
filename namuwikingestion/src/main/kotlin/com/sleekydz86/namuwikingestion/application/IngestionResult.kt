package com.sleekydz86.namuwikingestion.application

data class IngestionResult(
    val totalRowsRead: Int,
    val totalInserted: Int,
)
