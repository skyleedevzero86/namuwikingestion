package com.sleekydz86.namuwikingestion.presentation

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.NoHandlerFoundException

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RootRedirectHandler {

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFound(e: NoHandlerFoundException): ModelAndView {
        return ModelAndView("redirect:/")
    }
}
