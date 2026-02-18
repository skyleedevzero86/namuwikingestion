package com.sleekydz86.namuwikingestion.dataclass

import com.sleekydz86.namuwikingestion.global.enums.IngestionStatus

data class IngestionProgressState(
    val status: IngestionStatus,
    val rowsRead: Int,
    val inserted: Int,
    val startedAt: Long?,
    val finishedAt: Long?,
    val errorMessage: String?,
    val history: List<ProgressSnapshot>,
) {
    companion object {
        fun idle() = IngestionProgressState(
            status = IngestionStatus.IDLE,
            rowsRead = 0,
            inserted = 0,
            startedAt = null,
            finishedAt = null,
            errorMessage = null,
            history = emptyList(),
        )
    }
}
