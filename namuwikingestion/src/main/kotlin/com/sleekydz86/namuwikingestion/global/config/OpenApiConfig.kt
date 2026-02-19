package com.sleekydz86.namuwikingestion.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI().info(
        Info()
            .title("나무위키 수집 API")
            .version("1.0")
            .description("나무위키 수집, 검색, 임베딩 헬스 API.")
    )
}
