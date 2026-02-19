package com.sleekydz86.namuwikingestion.global.exception

import mu.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(e: AppException): ResponseEntity<ApiErrorResponse> {
        logger.warn(e) { "AppException: code=${e.code}, message=${e.message}" }
        val status = when (e) {
            is PersistenceException -> HttpStatus.SERVICE_UNAVAILABLE
            is SearchException -> HttpStatus.BAD_GATEWAY
            is EmbeddingUnavailableException -> HttpStatus.BAD_GATEWAY
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        return ResponseEntity.status(status).body(ApiErrorResponse(code = e.code, message = e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "처리되지 않은 예외" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(code = "INTERNAL_ERROR", message = "예기치 않은 오류가 발생했습니다.")
        )
    }
}
