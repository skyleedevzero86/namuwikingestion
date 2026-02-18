package com.sleekydz86.namuwikingestion.infrastructure.huggingface


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.avro.generic.GenericRecord
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.hadoop.util.HadoopInputFile
import com.sleekydz86.namuwikingestion.dataclass.NamuwikiRow
import com.sleekydz86.namuwikingestion.global.config.DatasetConfig
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service
class HfDatasetService(
    private val config: DatasetConfig,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()
    private val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun streamRows(): Sequence<NamuwikiRow> = sequence {
        val parquetFile = resolveParquetFile()
        try {
            val limit = if (config.limit > 0) config.limit else Int.MAX_VALUE
            var count = 0
            val conf = Configuration()
            val inputFile = HadoopInputFile.fromPath(Path(parquetFile.absolutePath), conf)
            AvroParquetReader.builder<GenericRecord>(inputFile).withConf(conf).build().use { reader ->
                var record: GenericRecord?
                while (reader.read().also { record = it } != null && count < limit) {
                    yield(recordToRow(record!!))
                    count++
                    if (count % 50_000 == 0) logger.info { "행 $count 건 읽음" }
                }
            }
            logger.info { "총 $count 건 행 읽기 완료" }
        } finally {
            if (parquetFile.name.startsWith("namuwiki-") && parquetFile.name.endsWith(".parquet")) {
                parquetFile.delete()
            }
        }
    }

    private fun resolveParquetFile(): File {
        val localPath = config.localParquetPath?.trim()?.takeIf { it.isNotEmpty() }
        if (localPath != null) {
            val file = File(localPath)
            if (file.isFile) {
                logger.info { "로컬 parquet 사용: ${file.absolutePath}" }
                return file
            }
            val fromWorkDir = File(System.getProperty("user.dir"), localPath)
            if (fromWorkDir.isFile) {
                logger.info { "로컬 parquet 사용: ${fromWorkDir.absolutePath}" }
                return fromWorkDir
            }
            throw IllegalArgumentException("로컬 parquet 파일 없음: $localPath 또는 ${fromWorkDir.absolutePath}")
        }
        val parquetUrl = fetchParquetUrl()
        logger.info { "parquet 다운로드 중: $parquetUrl" }
        return downloadToTemp(parquetUrl)
    }

    private fun fetchParquetUrl(): String {
        val commit = config.hfCommit?.trim()?.takeIf { it.isNotEmpty() }
        val fileName = config.parquetFileName?.trim()?.takeIf { it.isNotEmpty() }
        if (commit != null && fileName != null) {
            val direct = "https://huggingface.co/datasets/${config.hfDataset}/resolve/$commit/$fileName"
            logger.info { "지정 파일 직접 다운로드: $fileName (커밋 $commit)" }
            return direct
        }
        val url = "https://datasets-server.huggingface.co/parquet?dataset=${config.hfDataset.replace("/", "%2F")}"
        val request = Request.Builder().url(url).get().build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) throw RuntimeException("HF parquet API 실패: ${response.code}")
        val body = response.body?.string() ?: throw RuntimeException("응답 없음")
        val parsed = objectMapper.readValue<ParquetListResponse>(body)
        val files = parsed.parquetFiles.orEmpty()
        val train = files.find { it.split == config.split }
            ?: files.firstOrNull()
            ?: throw RuntimeException("스플릿 ${config.split}에 대한 parquet 파일 없음 (API 반환: ${files.size}개)")
        return train.url
    }

    private fun recordToRow(record: GenericRecord): NamuwikiRow {
        val title = record.get("title")?.toString() ?: ""
        val text = record.get("text")?.toString() ?: ""
        val namespace = record.get("namespace")?.toString()
        val contributors = record.get("contributors")?.toString()
        return NamuwikiRow(title = title, text = text, namespace = namespace, contributors = contributors)
    }

    private fun downloadToTemp(url: String): File {
        val request = Request.Builder().url(url).get().build()
        val response = http.newCall(request).execute()
        if (response.body == null) throw RuntimeException("응답 본문 없음")
        val dir = config.downloadDir?.trim()?.takeIf { it.isNotEmpty() }?.let { Paths.get(it) }
        if (dir != null) {
            Files.createDirectories(dir)
        }
        val tempFile = (dir?.let { Files.createTempFile(it, "namuwiki-", ".parquet") }
            ?: Files.createTempFile("namuwiki-", ".parquet")).toFile()
        logger.info { "parquet 저장 경로: ${tempFile.absolutePath}" }
        response.body!!.byteStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private data class ParquetListResponse(
        @JsonProperty("parquet_files") val parquetFiles: List<ParquetFileEntry>? = null
    )
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ParquetFileEntry(val url: String, val split: String)
}
