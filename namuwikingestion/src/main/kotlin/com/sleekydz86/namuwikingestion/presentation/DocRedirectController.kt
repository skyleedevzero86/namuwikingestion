package com.sleekydz86.namuwikingestion.presentation

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DocRedirectController {

    @GetMapping("/doc", "/doc/")
    fun redirectToSwaggerUi(): String = "redirect:/api-docs-unified.html"

    @GetMapping("/swagger-ui", "/swagger-ui/", "/swagger-ui.html")
    fun redirectToUnified(): String = "redirect:/api-docs-unified.html"
}
