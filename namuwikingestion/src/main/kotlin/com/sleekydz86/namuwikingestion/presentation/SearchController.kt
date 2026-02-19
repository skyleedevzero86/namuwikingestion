package com.sleekydz86.namuwikingestion.presentation

import com.sleekydz86.namuwikingestion.application.SearchResultDto
import com.sleekydz86.namuwikingestion.application.SearchService
import com.sleekydz86.namuwikingestion.global.util.SearchKeywordTokenizer
import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

private val logger = KotlinLogging.logger {}

@Controller
class SearchController(
    private val searchService: SearchService,
) {

    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        model: Model,
    ): String {
        model.addAttribute("query", q ?: "")
        model.addAttribute("searchKeywords", SearchKeywordTokenizer.toTokens(q))
        if (!q.isNullOrBlank()) {
            val results = try {
                searchService.search(q.trim(), limit.coerceIn(1, 100))
            } catch (e: Exception) {
                logger.warn(e) { "search 실패, query=$q, 빈 결과 반환" }
                model.addAttribute("searchError", true)
                emptyList<SearchResultDto>()
            }
            model.addAttribute("results", results)
        } else {
            model.addAttribute("results", emptyList<SearchResultDto>())
        }
        return "search"
    }

}
