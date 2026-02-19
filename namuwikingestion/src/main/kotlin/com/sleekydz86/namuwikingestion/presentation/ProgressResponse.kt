package com.sleekydz86.namuwikingestion.presentation

data class ProgressResponse(
    val status: String,
    val rowsRead: Int,
    val inserted: Int,
    val startedAt: Long?,
    val finishedAt: Long?,
    val errorMessage: String?,
    val history: List<SnapshotDto>,
)
