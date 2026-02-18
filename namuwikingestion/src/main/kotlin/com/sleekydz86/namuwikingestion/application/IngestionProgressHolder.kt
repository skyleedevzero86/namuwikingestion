package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.dataclass.IngestionProgressState
import com.sleekydz86.namuwikingestion.dataclass.ProgressSnapshot
import com.sleekydz86.namuwikingestion.global.enums.IngestionStatus
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class IngestionProgressHolder {

    private val state = AtomicReference(IngestionProgressState.idle())
    private val maxHistorySize = 120

    fun get(): IngestionProgressState = state.get()

    fun start() {
        state.set(
            IngestionProgressState(
                status = IngestionStatus.RUNNING,
                rowsRead = 0,
                inserted = 0,
                startedAt = System.currentTimeMillis(),
                finishedAt = null,
                errorMessage = null,
                history = emptyList(),
            )
        )
    }

    fun update(rowsRead: Int, inserted: Int) {
        val now = System.currentTimeMillis()
        val snapshot = ProgressSnapshot(at = now, rowsRead = rowsRead, inserted = inserted)
        state.updateAndGet { current ->
            val newHistory = (current.history + snapshot).takeLast(maxHistorySize)
            current.copy(rowsRead = rowsRead, inserted = inserted, history = newHistory)
        }
    }

    fun done(rowsRead: Int, inserted: Int) {
        val now = System.currentTimeMillis()
        val snapshot = ProgressSnapshot(at = now, rowsRead = rowsRead, inserted = inserted)
        state.updateAndGet { current ->
            val newHistory = (current.history + snapshot).takeLast(maxHistorySize)
            current.copy(
                status = IngestionStatus.DONE,
                rowsRead = rowsRead,
                inserted = inserted,
                finishedAt = now,
                history = newHistory,
            )
        }
    }

    fun error(message: String) {
        state.updateAndGet { it.copy(status = IngestionStatus.ERROR, errorMessage = message) }
    }

    fun reset() {
        state.set(IngestionProgressState.idle())
    }
}
