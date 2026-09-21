package com.example.whitelistcheck

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.os.SystemClock
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException

class NetworkChecker {

    companion object {
        const val METHOD_HEAD = "HEAD"
        const val METHOD_GET  = "GET"
        const val METHOD_PING = "PING"
    }

    private val userAgents = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 15; SM-S931B Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/127.0.6533.103 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 15; SM-S931U Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/132.0.6834.163 Mobile Safari/537.36",
        "Mozila/5.0 (Linux; Android 14; SM-S928B/DS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
        "Mozila/5.0 (Linux; Android 14; SM-S928W) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-F9560 Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/127.0.6533.103 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-F956U) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/80.0.3987.119 Mobile Safari/537.36",
        "Mozilla/5.0 (Android 15; Mobile; SM-G556B/DS; rv:130.0) Gecko/130.0 Firefox/130.0",
        "Mozilla/5.0 (Android 15; Mobile; SM-G556B; rv:130.0) Gecko/130.0 Firefox/130.0",
        "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S911U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
    )

    private val client = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(12))
        .connectTimeout(java.time.Duration.ofSeconds(8))
        .readTimeout(java.time.Duration.ofSeconds(8))
        .followRedirects(true)
        .build()

    /**
     * Runs [attempts] sequential checks and returns the median time on success.
     * [onAttempt] is called before each attempt so the UI can show progress.
     */
    suspend fun check(
        url: String,
        method: String = METHOD_HEAD,
        attempts: Int = 3,
        onAttempt: suspend (attempt: Int) -> Unit = {}
    ): CheckState {
        var lastFailure: CheckState.Failure? = null

        for (i in 1..attempts) {
            onAttempt(i)
            when (val result = singleAttempt(url, method)) {
                is CheckState.Success -> return result
                is CheckState.Failure -> lastFailure = result
                else -> {}
            }
        }

        return lastFailure ?: CheckState.Failure(null, "Ошибка соединения", 0)
    }

    // ──────────────────────────────────────────────
    //  Single attempt dispatcher
    // ──────────────────────────────────────────────

    private fun singleAttempt(url: String, method: String): CheckState =
        if (method == METHOD_PING) pingAttempt(extractHost(url))
        else httpAttempt(url, useGet = method == METHOD_GET)

    // ──────────────────────────────────────────────
    //  HTTP (HEAD / GET)
    // ──────────────────────────────────────────────

    private fun httpAttempt(url: String, useGet: Boolean): CheckState {
        val timing = mutableMapOf<String, Long>()
        val stage = AtomicReference<Stage?>(null)
        val listener = object : EventListener() {
            override fun dnsStart(call: Call, domainName: String) { stage.set(Stage.DNS) }
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                stage.set(Stage.TCP)
                timing["connectStart"] = SystemClock.elapsedRealtime()
            }
            override fun secureConnectStart(call: Call) { stage.set(Stage.TLS) }
            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
                timing["connectEnd"] = SystemClock.elapsedRealtime()
            }
            override fun requestHeadersStart(call: Call) { stage.set(Stage.REQUEST) }
            override fun responseHeadersStart(call: Call) { stage.set(Stage.RESPONSE) }
            override fun responseHeadersEnd(call: Call, response: Response) {
                timing["responseEnd"] = SystemClock.elapsedRealtime()
            }
        }

        val localClient = client.newBuilder().eventListener(listener).build()
        val start = SystemClock.elapsedRealtime()

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgents.random())
                .apply { if (useGet) get() else head() }
                .build()

            localClient.newCall(request).execute().use { response ->
                val rtt = if (timing.containsKey("connectStart") && timing.containsKey("responseEnd"))
                    timing["responseEnd"]!! - timing["connectStart"]!!
                else
                    SystemClock.elapsedRealtime() - start

                if (response.isSuccessful || response.code in 300..399) {
                    CheckState.Success(response.code, rtt)
                } else {
                    val msg = if (response.message.isNotBlank()) "HTTP ${response.code}: ${response.message}"
                              else "HTTP ${response.code}"
                    CheckState.Failure(response.code, msg, rtt)
                }
            }
        } catch (e: IOException) {
            CheckState.Failure(null, describeError(stage.get(), e), 0)
        }
    }

    private enum class Stage(val label: String) {
        DNS("DNS"),
        TCP("TCP-соединение"),
        TLS("TLS-рукопожатие"),
        REQUEST("отправка запроса"),
        RESPONSE("получение ответа"),
    }

    /** Builds "<stage>: <reason> — <details>" so the UI shows where the request broke. */
    private fun describeError(stage: Stage?, e: IOException): String {
        val reason = when (e) {
            is UnknownHostException -> "хост не найден"
            is InterruptedIOException -> "таймаут"          // also covers SocketTimeoutException
            is SSLException -> "ошибка TLS"
            is ConnectException -> "соединение отклонено или сеть недоступна"
            is SocketException -> "соединение разорвано"
            else -> null
        }
        val details = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        val head = listOfNotNull(stage?.label, reason).joinToString(": ")
        return when {
            head.isEmpty() -> details
            reason == null -> "$head: $details"
            else -> "$head — $details"
        }
    }

    // ──────────────────────────────────────────────
    //  ICMP Ping
    // ──────────────────────────────────────────────

    private fun pingAttempt(host: String): CheckState {
        val start = SystemClock.elapsedRealtime()
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-w", "5", host))
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            val elapsed = SystemClock.elapsedRealtime() - start

            if (exitCode == 0) {
                // Parse "time=12.3 ms" or "time=12" from ping output
                val timeMs = Regex("time=(\\d+\\.?\\d*)").find(output)
                    ?.groupValues?.get(1)?.toDoubleOrNull()?.toLong() ?: elapsed
                CheckState.Success(0, timeMs)
            } else {
                CheckState.Failure(null, "Хост недоступен (ICMP)", elapsed)
            }
        } catch (e: Exception) {
            CheckState.Failure(null, e.message ?: "Ошибка ping", SystemClock.elapsedRealtime() - start)
        } finally {
            process?.destroy()
        }
    }

    private fun extractHost(url: String): String =
        try { java.net.URL(url).host }
        catch (e: Exception) {
            url.removePrefix("https://").removePrefix("http://").split("/").first()
        }
}
