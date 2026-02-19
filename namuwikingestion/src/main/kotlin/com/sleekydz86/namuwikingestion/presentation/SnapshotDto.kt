package com.sleekydz86.namuwikingestion.presentation

data class SnapshotDto(
    val at: Long,
    val rowsRead: Int,
    val inserted: Int,
)
