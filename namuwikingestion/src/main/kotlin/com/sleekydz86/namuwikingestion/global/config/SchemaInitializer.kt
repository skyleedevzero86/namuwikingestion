package com.sleekydz86.namuwikingestion.global.config

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.sql.DataSource

@Component
class SchemaInitializer(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(javaClass)

    @jakarta.annotation.PostConstruct
    fun runSchema() {
        Thread {
            runSchemaBlocking()
        }.apply { isDaemon = false; start() }
    }

    private fun runSchemaBlocking() {
        try {
            dataSource.connection.use { conn ->
                val resource = EncodedResource(ClassPathResource("schema.sql"), StandardCharsets.UTF_8)
                ScriptUtils.executeSqlScript(conn, resource)
            }
            log.info("DB 스키마 적용 완료")
        } catch (e: Exception) {
            log.warn("DB 스키마 적용 실패 (PostgreSQL 미기동 또는 연결 불가). DB 기동 후 앱을 재시작하세요. 원인: {}", e.message)
            return
        }

        try {
            dataSource.connection.use { conn ->
                val resource = EncodedResource(ClassPathResource("schema-fulltext.sql"), StandardCharsets.UTF_8)
                ScriptUtils.executeSqlScript(conn, resource)
            }
            log.info("DB 전문 검색 스키마( textsearch_ko, tsv_content, GIN, 트리거 ) 적용 완료")
        } catch (e: Exception) {
            log.warn(
                "DB 전문 검색 스키마 적용 실패( textsearch_ko 미설치 시 정상 ). fulltext-regconfig=simple 로 동작합니다. 원인: {}",
                e.message
            )
        }
    }
}
