package com.sleekydz86.namuwikingestion.global.config

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@ConditionalOnBean(DataSource::class)
class DataSourceUrlLogger(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(javaClass)

    @jakarta.annotation.PostConstruct
    fun logUrl() {
        val url = (dataSource as? HikariDataSource)?.jdbcUrl ?: "unknown"
        log.info("DB 연결 URL: {}", url)
        try {
            dataSource.connection.use { }
            log.info("PostgreSQL 연결 성공")
        } catch (e: Exception) {
            log.error(
                "PostgreSQL 연결 실패 (127.0.0.1:5432에 DB가 없습니다). " +
                "해결: 터미널에서 프로젝트 폴더(namuwikingestion)로 이동 후 'docker-compose up -d' 실행 후 앱 재시작."
            )
        }
    }
}
