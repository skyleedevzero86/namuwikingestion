package com.sleekydz86.namuwikingestion.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "namuwiki.search")
data class FullTextSearchConfig(
    val fulltextRegconfig: String = "korean",
    val rrfK: Int = 60,
) {
    fun useTsvContent(): Boolean = fulltextRegconfig.equals("korean", ignoreCase = true)
}
