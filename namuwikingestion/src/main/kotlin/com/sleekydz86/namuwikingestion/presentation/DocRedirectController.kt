package com.sleekydz86.namuwikingestion.presentation

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DocRedirectController {

    @GetMapping("/doc", "/doc/")
    fun redirectToSwaggerUi(): String = "redirect:/swagger-ui/index.html"
}
