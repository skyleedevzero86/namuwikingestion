package com.sleekydz86.namuwikingestion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File

@SpringBootApplication
class NamuwikingestionApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (System.getProperty("hadoop.home.dir").isNullOrBlank()) {
                val hadoopHome = File(System.getProperty("java.io.tmpdir"), "namuwiki-hadoop-dummy").apply {
                    if (!exists()) mkdirs()
                    File(this, "bin").takeIf { !it.exists() }?.mkdirs()
                }
                System.setProperty("hadoop.home.dir", hadoopHome.absolutePath)
            }
            runApplication<NamuwikingestionApplication>(*args)
        }
    }
}
