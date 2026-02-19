package com.sleekydz86.namuwikingestion.presentation

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RootRedirectHandler {

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandler(e: NoHandlerFoundException): ModelAndView {
        return ModelAndView("redirect:/")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ModelAndView {
        return ModelAndView("redirect:/")
    }
}
