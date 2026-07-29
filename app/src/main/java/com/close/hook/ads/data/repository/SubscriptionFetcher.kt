package com.close.hook.ads.data.repository

import com.close.hook.ads.data.model.SubscriptionSource
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

internal object SubscriptionFetcher {

    private const val MAX_RESPONSE_BYTES = 20 * 1024 * 1024

    sealed interface Result {
        data object NotModified : Result
        data class Content(val input: ByteArrayInputStream, val etag: String?, val lastModified: String?) : Result
    }

    fun fetch(source: SubscriptionSource): Result {
        val connection = (URL(source.url).openConnection() as? HttpURLConnection)
            ?: throw IllegalArgumentException("Unsupported subscription URL")
        connection.apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "text/plain, text/*, */*")
            source.etag?.let { setRequestProperty("If-None-Match", it) }
            source.lastModified?.let { setRequestProperty("If-Modified-Since", it) }
        }

        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Result.NotModified
                in 200..299 -> {
                    val bytes = connection.inputStream.use { stream ->
                        stream.readBytesLimited(MAX_RESPONSE_BYTES)
                    }
                    Result.Content(
                        ByteArrayInputStream(bytes),
                        connection.getHeaderField("ETag"),
                        connection.getHeaderField("Last-Modified")
                    )
                }
                else -> throw IllegalStateException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) throw IllegalArgumentException("Subscription exceeds ${limit / 1024 / 1024} MiB")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
