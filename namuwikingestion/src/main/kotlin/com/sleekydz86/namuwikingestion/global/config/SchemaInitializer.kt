package com.sleekydz86.namuwikingestion.global.config

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.sql.DataSource

@Component
class SchemaInitializer(private val dataSource: DataSource) {

    @jakarta.annotation.PostConstruct
    fun runSchema() {
        dataSource.connection.use { conn ->
            val resource = EncodedResource(ClassPathResource("schema.sql"), StandardCharsets.UTF_8)
            ScriptUtils.executeSqlScript(conn, resource)
        }
    }
}
