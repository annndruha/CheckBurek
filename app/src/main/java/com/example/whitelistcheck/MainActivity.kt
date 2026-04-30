package com.example.whitelistcheck

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(10))
        .build()

    private val whitelistUrls = listOf(
        "https://ya.ru",
        "https://dzen.ru",
        "https://max.ru",
        "https://vk.com",
        "https://2gis.ru",
        "https://sberbank.ru"
    )

    private val nonWhitelistUrls = listOf(
        "https://store.steampowered.com",
        "https://habr.com",
        "https://ru.wikipedia.org"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btn = findViewById<android.widget.Button>(R.id.btnCheck)
        val tv = findViewById<android.widget.TextView>(R.id.tvResult)

        btn.setOnClickListener {
            tv.text = "Проверка..."
            CoroutineScope(Dispatchers.IO).launch {
                val report = checkNetwork()
                withContext(Dispatchers.Main) {
                    tv.text = report
                }
            }
        }
    }

    private suspend fun checkNetwork(): String {
        val sb = StringBuilder()

        var whitelistOk = true
        var nonWhitelistBlocked = false

        sb.append("WHITE LIST:\n")
        for (url in whitelistUrls) {
            val ok = ping(url)
            whitelistOk = whitelistOk && ok
            sb.append("$url -> ${status(ok)}\n")
        }

        sb.append("\nNON-WHITE LIST:\n")
        for (url in nonWhitelistUrls) {
            val ok = ping(url)
            if (!ok) nonWhitelistBlocked = true
            sb.append("$url -> ${status(ok)}\n")
        }

        sb.append("\nАГРЕГИРОВАННЫЙ СТАТУС:\n")
        sb.append(
            if (whitelistOk && nonWhitelistBlocked)
                "УСТРОЙСТВО В СЕТИ С БЕЛЫМИ СПИСКАМИ"
            else
                "ОБЫЧНАЯ СЕТЬ ИЛИ НЕТ ДОСТУПА"
        )

        return sb.toString()
    }

    private fun ping(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            client.newCall(request).execute().use {
                it.isSuccessful
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun status(ok: Boolean) =
        if (ok) "ДОСТУПЕН" else "НЕДОСТУПЕН"
}