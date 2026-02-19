package com.sleekydz86.namuwikingestion.presentation

import com.sleekydz86.namuwikingestion.application.IngestionProgressHolder
import com.sleekydz86.namuwikingestion.application.IngestionRunner
import com.sleekydz86.namuwikingestion.global.enums.IngestionStatus
import com.sleekydz86.namuwikingestion.infrastructure.embedding.EmbeddingHealthCheck
import com.sleekydz86.namuwikingestion.infrastructure.persistence.NamuwikiDocRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "수집", description = "나무위키 수집, 진행 상황, 통계, 임베딩 헬스")
class IngestionController(
    private val ingestionRunner: IngestionRunner,
    private val progressHolder: IngestionProgressHolder,
    private val docRepository: NamuwikiDocRepository,
    private val embeddingHealthCheck: EmbeddingHealthCheck,
) {

    @Operation(summary = "수집 시작", description = "나무위키 비동기 수집을 시작합니다. 이미 진행 중이면 429를 반환합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "수집 시작됨"),
        ApiResponse(responseCode = "429", description = "수집이 이미 진행 중"),
    )
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

    @Operation(summary = "수집 진행 상황 조회", description = "현재 상태, 읽은/삽입한 행 수, 이력을 반환합니다.")
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

    @Operation(summary = "문서 개수 조회", description = "namuwiki_doc 테이블의 전체 문서 개수를 반환합니다.")
    @GetMapping("/stats")
    fun stats(): ResponseEntity<StatsResponse> {
        val count = docRepository.count()
        return ResponseEntity.ok(StatsResponse(documentCount = count))
    }

    @Operation(summary = "임베딩 서비스 헬스", description = "임베딩 서버 연결을 확인하고 모델 정보를 반환합니다.")
    @GetMapping("/embedding-health")
    fun embeddingHealth(): ResponseEntity<EmbeddingHealthResponse> {
        val result = embeddingHealthCheck.check()
        return ResponseEntity.ok(
            EmbeddingHealthResponse(
                ok = result.ok,
                healthUrl = result.healthUrl,
                model = result.model,
            )
        )
    }

    data class EmbeddingHealthResponse(val ok: Boolean, val healthUrl: String?, val model: String?)
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
