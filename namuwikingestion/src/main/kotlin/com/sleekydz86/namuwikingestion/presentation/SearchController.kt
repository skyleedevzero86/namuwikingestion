package com.sleekydz86.namuwikingestion.presentation

import com.sleekydz86.namuwikingestion.application.SearchService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

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
        if (!q.isNullOrBlank()) {
            val results = try {
                searchService.search(q.trim(), limit.coerceIn(1, 100))
            } catch (e: Exception) {
                emptyList<SearchService.SearchResultDto>()
            }
            model.addAttribute("results", results)
        } else {
            model.addAttribute("results", emptyList<SearchService.SearchResultDto>())
        }
        return "search"
    }
}
