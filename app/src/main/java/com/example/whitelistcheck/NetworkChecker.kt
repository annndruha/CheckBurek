package com.example.whitelistcheck

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.os.SystemClock
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.*


class NetworkChecker {
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
        "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Mobile Safari/537.36 Dalvik/2.1.0 (Linux; U; Android 13; SM-S911B Build/TP1A.220624.014)",
        "Mozilla/5.0 (Linux; Android 13; SM-S911U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S901U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S908U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G991U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G998U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-A536U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-A515F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-A515U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
    )

    private val client = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(8))
        .connectTimeout(java.time.Duration.ofSeconds(8))
        .readTimeout(java.time.Duration.ofSeconds(8))
        .followRedirects(true)
        .build()


    suspend fun check(url: String, attempts: Int = 3): CheckState = coroutineScope {
        val results = (1..attempts)
            .map { async(Dispatchers.IO) { singleAttempt(url) } }
            .awaitAll()

        val successes = results.filterIsInstance<CheckState.Success>()
        if (successes.isNotEmpty()) {
            val median = successes.map { it.timeMs }.sorted()[successes.size / 2]
            CheckState.Success(successes.first().code, median)
        } else {
            results.filterIsInstance<CheckState.Failure>().firstOrNull()
                ?: CheckState.Failure(null, "Ошибка соединения", 0)
        }
    }

    private fun singleAttempt(url: String): CheckState {
        val timing = mutableMapOf<String, Long>()
        val listener = object : EventListener() {
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                timing["connectStart"] = SystemClock.elapsedRealtime()
            }
            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
                timing["connectEnd"] = SystemClock.elapsedRealtime()
            }
            override fun responseHeadersEnd(call: Call, response: Response) {
                timing["responseEnd"] = SystemClock.elapsedRealtime()
            }
        }

        val localClient = client.newBuilder().eventListener(listener).build()
        val start = SystemClock.elapsedRealtime()

        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgents.random())
                .build()

            localClient.newCall(request).execute().use { response ->
                val rtt = if (timing.containsKey("connectStart") && timing.containsKey("responseEnd")) {
                    timing["responseEnd"]!! - timing["connectStart"]!!
                } else {
                    SystemClock.elapsedRealtime() - start
                }
                if (response.isSuccessful || response.code in 300..399) {
                    CheckState.Success(response.code, rtt)
                } else {
                    val msg = if (response.message.isNotBlank()) "HTTP ${response.code}: ${response.message}"
                    else "HTTP ${response.code}"
                    CheckState.Failure(response.code, msg, rtt)
                }
            }
        } catch (e: IOException) {
            CheckState.Failure(null, e.message ?: "Ошибка соединения", 0)
        }
    }
}
