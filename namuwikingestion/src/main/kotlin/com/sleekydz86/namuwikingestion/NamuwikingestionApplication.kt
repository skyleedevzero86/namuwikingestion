package com.sleekydz86.namuwikingestion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NamuwikingestionApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<NamuwikingestionApplication>(*args)
        }
    }
}
