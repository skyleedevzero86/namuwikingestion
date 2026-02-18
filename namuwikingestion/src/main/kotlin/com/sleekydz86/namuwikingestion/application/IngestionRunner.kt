package com.sleekydz86.namuwikingestion.application

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class IngestionRunner(
    private val ingestionService: NamuwikiIngestionService,
) {

    @Async
    fun runAsync() {
        try {
            ingestionService.runIngestion()
        } catch (_: Exception) {
        }
    }
}
