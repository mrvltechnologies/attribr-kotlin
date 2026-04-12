package com.mrvltechnologies.attribr

import java.net.HttpURLConnection
import java.net.URL

/** Result of a single network attempt. */
internal sealed class NetworkResult {
    data class Success(val statusCode: Int, val body: String) : NetworkResult()
    data class Failure(val statusCode: Int?, val error: Exception?) : NetworkResult()
}

/**
 * Minimal HTTP client built on [HttpURLConnection].
 *
 * - Zero third-party dependencies.
 * - Attaches `X-Attribr-Key` header on every request.
 * - Retries retryable failures (connection errors, 429, 5xx) with
 *   truncated exponential back-off: 1 s → 2 s → 4 s (max 3 attempts by default).
 * - Non-retryable failures (4xx except 429) are returned immediately.
 */
internal class NetworkClient(
    private val apiKey: String,
    private val config: AttribrConfiguration,
    private val logger: AttribrLogger,
) {
    /**
     * POST [bodyJson] to [config.baseUrl]/[endpoint].
     * Retries on connection errors and retryable HTTP status codes.
     */
    fun post(endpoint: String, bodyJson: String): NetworkResult =
        executeWithRetry("POST", endpoint, bodyJson)

    /**
     * DELETE with [bodyJson] body to [config.baseUrl]/[endpoint].
     * No retry — deletion is idempotent from the server side.
     */
    fun delete(endpoint: String, bodyJson: String): NetworkResult =
        perform("DELETE", "${config.baseUrl}/$endpoint", bodyJson)

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun executeWithRetry(method: String, endpoint: String, bodyJson: String): NetworkResult {
        val url = "${config.baseUrl}/$endpoint"
        var lastResult: NetworkResult = NetworkResult.Failure(null, null)

        repeat(config.maxRetries) { attempt ->
            if (attempt > 0) {
                // Truncated exponential back-off: 1 s, 2 s, 4 s …
                val delayMs = (1_000L * (1L shl (attempt - 1))).coerceAtMost(8_000L)
                logger.debug("Retry $attempt/${config.maxRetries} for $endpoint in ${delayMs}ms")
                Thread.sleep(delayMs)
            }

            val result = perform(method, url, bodyJson)
            lastResult = result

            when (result) {
                is NetworkResult.Success -> {
                    logger.debug("$method $endpoint → ${result.statusCode}")
                    return result
                }
                is NetworkResult.Failure -> {
                    val code = result.statusCode
                    val isRetryable = code == null || code == 429 || code in 500..599
                    if (!isRetryable) {
                        logger.error("$method $endpoint → non-retryable $code")
                        return result
                    }
                    logger.debug("$method $endpoint failed (attempt ${attempt + 1}/${config.maxRetries}) → $code")
                }
            }
        }

        return lastResult
    }

    private fun perform(method: String, urlString: String, bodyJson: String): NetworkResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod    = method
                connectTimeout   = config.requestTimeoutMs
                readTimeout      = config.requestTimeoutMs
                doOutput         = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Attribr-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val responseBody = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            } catch (e: Exception) {
                ""
            }

            if (code in 200..299) {
                NetworkResult.Success(code, responseBody)
            } else {
                NetworkResult.Failure(code, null)
            }
        } catch (e: Exception) {
            logger.error("Network error for $urlString: ${e.message}")
            NetworkResult.Failure(null, e)
        } finally {
            conn?.disconnect()
        }
    }
}
