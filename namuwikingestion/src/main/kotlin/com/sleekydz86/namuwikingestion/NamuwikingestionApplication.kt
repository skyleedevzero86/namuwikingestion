package com.sleekydz86.namuwikingestion

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File

@SpringBootApplication
class NamuwikingestionApplication {
    companion object {
        private const val WINUTILS_URL = "https://github.com/cdarlint/winutils/raw/master/hadoop-3.3.5/bin/winutils.exe"

        @JvmStatic
        fun main(args: Array<String>) {
            if (System.getProperty("hadoop.home.dir").isNullOrBlank()) {
                val hadoopHome = File(System.getProperty("java.io.tmpdir"), "namuwiki-hadoop-dummy").apply {
                    if (!exists()) mkdirs()
                    File(this, "bin").takeIf { !it.exists() }?.mkdirs()
                }
                System.setProperty("hadoop.home.dir", hadoopHome.absolutePath)
                if (System.getProperty("os.name").lowercase().contains("win")) {
                    ensureWinutils(hadoopHome)
                }
            }
            runApplication<NamuwikingestionApplication>(*args)
        }

        private fun ensureWinutils(hadoopHome: File) {
            val binDir = File(hadoopHome, "bin")
            val winutils = File(binDir, "winutils.exe")
            if (winutils.exists()) return
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(WINUTILS_URL).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            winutils.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                System.err.println(
                    "[namuwiki] Windows: winutils.exe not found. Run: .\\scripts\\setup-winutils.ps1 " +
                    "or download from ${WINUTILS_URL} into ${winutils.absolutePath}"
                )
            }
        }
    }
}
