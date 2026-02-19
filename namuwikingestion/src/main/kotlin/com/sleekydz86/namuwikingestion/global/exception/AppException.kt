package com.sleekydz86.namuwikingestion.global.exception

open class AppException(
    val code: String,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

class PersistenceException(
    message: String = "저장소 작업 실패",
    cause: Throwable? = null,
) : AppException("PERSISTENCE_ERROR", message, cause)

class SearchException(
    message: String = "검색 작업 실패",
    cause: Throwable? = null,
) : AppException("SEARCH_ERROR", message, cause)

class EmbeddingUnavailableException(
    message: String = "임베딩 서비스를 사용할 수 없음",
    cause: Throwable? = null,
) : AppException("EMBEDDING_UNAVAILABLE", message, cause)
