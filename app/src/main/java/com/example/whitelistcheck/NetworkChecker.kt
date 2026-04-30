package com.example.whitelistcheck

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class NetworkChecker {
    private val client = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(10))
        .connectTimeout(java.time.Duration.ofSeconds(8))
        .readTimeout(java.time.Duration.ofSeconds(8))
        .followRedirects(true)
        .build()

    fun check(url: String): CheckState {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                val time = System.currentTimeMillis() - start
                if (response.isSuccessful || response.code in 300..399) {
                    CheckState.Success(response.code, time)
                } else {
                    CheckState.Failure(response.code, "HTTP ${response.code}: ${response.message}", time)
                }
            }
        } catch (e: IOException) {
            val time = System.currentTimeMillis() - start
            CheckState.Failure(null, e.message ?: "Ошибка соединения", time)
        } catch (e: Exception) {
            val time = System.currentTimeMillis() - start
            CheckState.Failure(null, e.message ?: "Неизвестная ошибка", time)
        }
    }
}
