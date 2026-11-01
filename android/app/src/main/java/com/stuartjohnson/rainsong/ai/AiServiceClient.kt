package com.stuartjohnson.rainsong.ai

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ComposeResult(
    val wav: ByteArray,
    val arrangement: Arrangement,
)

/**
 * HTTP client for the `ai/` container. No API keys on the phone.
 * See `docs/ai-interface.md`.
 */
class AiServiceClient {
    fun health(baseUrl: String): Boolean {
        val root = normalizeBase(baseUrl) ?: return false
        return try {
            val conn = (URL("$root/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 800
                readTimeout = 800
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode !in 200..299) return false
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                body.contains("rainsong-ai") || body.trim() == "ok"
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun compose(baseUrl: String, summary: RainPatternSummary, durationSec: Int = 8): ComposeResult? {
        val root = normalizeBase(baseUrl) ?: return null
        val body = """{"dropsPerSecond":${summary.dropsPerSecond},"meanStrength":${summary.meanStrength},"lostHits":${summary.lostHits},"duration":$durationSec}"""
        val conn = (URL("$root/compose").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 240_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/wav")
        }
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            if (conn.responseCode !in 200..299) return null
            val header = header(conn, "X-RainSong-Arrangement")
            val wav = conn.inputStream.use { it.readBytes() }
            val fallback = Arrangement.fromIntensity(summary)
            val arrangement = if (header.isBlank()) fallback else Arrangement.parseJson(header, fallback)
            ComposeResult(wav, arrangement)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private fun header(conn: HttpURLConnection, name: String): String {
            conn.headerFields.forEach { (key, values) ->
                if (key != null && key.equals(name, ignoreCase = true)) {
                    return values?.firstOrNull().orEmpty()
                }
            }
            return conn.getHeaderField(name).orEmpty()
        }

        fun normalizeBase(raw: String): String? {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return null
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = "http://$s"
            }
            return s
        }
    }
}
