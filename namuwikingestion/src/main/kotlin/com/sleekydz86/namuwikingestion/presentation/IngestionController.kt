package com.sleekydz86.namuwikingestion.presentation


import com.sleekydz86.namuwikingestion.application.IngestionProgressHolder
import com.sleekydz86.namuwikingestion.application.IngestionRunner
import com.sleekydz86.namuwikingestion.global.enums.IngestionStatus
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class IngestionController(
    private val ingestionRunner: IngestionRunner,
    private val progressHolder: IngestionProgressHolder,
    private val docRepository: NamuwikiDocRepository,
) {

    @PostMapping("/ingest")
    fun startIngestion(): ResponseEntity<IngestionResponse> {
        val current = progressHolder.get()
        if (current.status == IngestionStatus.RUNNING) {
            return ResponseEntity.status(429).body(
                IngestionResponse(message = "수집이 이미 진행 중입니다")
            )
        }
        progressHolder.reset()
        ingestionRunner.runAsync()
        return ResponseEntity.accepted().body(IngestionResponse(message = "시작됨"))
    }

    @GetMapping("/progress")
    fun progress(): ResponseEntity<ProgressResponse> {
        val s = progressHolder.get()
        val body = ProgressResponse(
            status = s.status.name,
            rowsRead = s.rowsRead,
            inserted = s.inserted,
            startedAt = s.startedAt,
            finishedAt = s.finishedAt,
            errorMessage = s.errorMessage,
            history = s.history.map { SnapshotDto(it.at, it.rowsRead, it.inserted) },
        )
        return ResponseEntity.ok(body)
    }

    @GetMapping("/stats")
    fun stats(): ResponseEntity<StatsResponse> {
        val count = docRepository.count()
        return ResponseEntity.ok(StatsResponse(documentCount = count))
    }

    data class IngestionResponse(val message: String)

    data class ProgressResponse(
        val status: String,
        val rowsRead: Int,
        val inserted: Int,
        val startedAt: Long?,
        val finishedAt: Long?,
        val errorMessage: String?,
        val history: List<SnapshotDto>,
    )

    data class SnapshotDto(val at: Long, val rowsRead: Int, val inserted: Int)

    data class StatsResponse(val documentCount: Long)
}
