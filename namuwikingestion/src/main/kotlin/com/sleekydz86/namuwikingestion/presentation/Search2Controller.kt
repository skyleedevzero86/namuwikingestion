package com.sleekydz86.namuwikingestion.presentation

import com.sleekydz86.namuwikingestion.application.HybridSearchResult
import com.sleekydz86.namuwikingestion.application.HybridSearchResultDto
import com.sleekydz86.namuwikingestion.application.HybridSearchService
import com.sleekydz86.namuwikingestion.global.util.SearchKeywordTokenizer
import com.sleekydz86.namuwikingestion.infrastructure.persistence.SearchUiConfigRepository
import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

private val logger = KotlinLogging.logger {}

@Controller
class Search2Controller(
    private val hybridSearchService: HybridSearchService,
    private val searchUiConfigRepository: SearchUiConfigRepository,
) {

    @GetMapping("/search2")
    fun search2(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "IVF") vectorMode: String,
        @RequestParam(required = false) enableBm25: Boolean?,
        @RequestParam(defaultValue = "0.5") keywordWeight: Double,
        @RequestParam(defaultValue = "20") limit: Int,
        model: Model,
    ): String {
        model.addAttribute("query", q ?: "")
        model.addAttribute("searchKeywords", SearchKeywordTokenizer.toTokens(q))
        model.addAttribute("vectorMode", vectorMode.ifBlank { "IVF" })
        model.addAttribute("enableBm25", enableBm25 ?: true)
        model.addAttribute("keywordWeight", keywordWeight.coerceIn(0.0, 1.0))
        model.addAttribute("semanticWeight", (1.0 - keywordWeight).coerceIn(0.0, 1.0))

        model.addAttribute("sqlSectionLabel", searchUiConfigRepository.getValue("search2.sqlSectionLabel") ?: "생성된 SQL")
        model.addAttribute("explanationSectionLabel", searchUiConfigRepository.getValue("search2.explanationSectionLabel") ?: "쿼리 설명")

        if (!q.isNullOrBlank()) {
            val result = try {
                hybridSearchService.search(
                    query = q.trim(),
                    vectorMode = vectorMode.ifBlank { "IVF" },
                    enableBm25 = enableBm25 ?: true,
                    keywordWeight = keywordWeight.coerceIn(0.0, 1.0),
                    limit = limit.coerceIn(1, 100),
                )
            } catch (e: Exception) {
                logger.warn(e) { "search2 실패, query=$q, 오류 플레이스홀더 반환" }
                val errSql = searchUiConfigRepository.getValue("search2.errorSqlPlaceholder") ?: "-- 오류 발생"
                val errExplain = searchUiConfigRepository.getValue("search2.errorExplanationPlaceholder") ?: "[]"
                HybridSearchResult(emptyList(), errSql, errExplain)
            }
            model.addAttribute("results", result.results)
            model.addAttribute("generatedSql", result.generatedSql)
            model.addAttribute("queryExplanation", result.queryExplanation)
        } else {
            model.addAttribute("results", emptyList<HybridSearchResultDto>())
            model.addAttribute("generatedSql", searchUiConfigRepository.getValue("search2.emptySqlPlaceholder") ?: "-- 검색어 입력 후 SEARCH")
            model.addAttribute("queryExplanation", searchUiConfigRepository.getValue("search2.emptyExplanationPlaceholder") ?: "[]")
        }
        return "search2"
    }

}
