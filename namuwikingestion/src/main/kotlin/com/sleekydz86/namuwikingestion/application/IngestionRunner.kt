package com.sleekydz86.namuwikingestion.application

import mu.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class IngestionRunner(
    private val ingestionService: NamuwikiIngestionService,
) {

    @Async
    fun runAsync() {
        try {
            ingestionService.runIngestion()
        } catch (e: Exception) {
            logger.warn(e) { "runAsync: 수집 실패 (NamuwikiIngestionService에서 progressHolder.error 이미 호출됨)" }
        }
    }
}
