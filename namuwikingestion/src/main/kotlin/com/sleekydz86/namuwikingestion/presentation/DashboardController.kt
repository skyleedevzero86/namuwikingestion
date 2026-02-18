package com.sleekydz86.namuwikingestion.presentation

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController {

    @GetMapping("/")
    fun dashboard(): String = "dashboard"
}
