package com.sleekydz86.namuwikingestion.dataclass

import java.time.Instant

data class ProgressSnapshot(
    val at: Long,
    val rowsRead: Int,
    val inserted: Int,
) {
    val atInstant: Instant get() = Instant.ofEpochMilli(at)
}
