package com.example.whitelistcheck

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class StorageManager(context: Context) {
    private val prefs = context.getSharedPreferences("network_checker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveGroups(groups: List<UrlGroup>) {
        prefs.edit().putString("groups_v2", gson.toJson(groups)).apply()
    }

    fun loadGroups(): MutableList<UrlGroup> {
        val json = prefs.getString("groups_v2", null)
        val groups: MutableList<UrlGroup> = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<UrlGroup>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                getDefaultGroups()
            }
        } else {
            getDefaultGroups()
        }
        // Reset transient check states after deserialization
        groups.forEach { group ->
            group.urls.forEach { entry -> entry.state = CheckState.Idle }
        }
        return groups
    }

    fun saveMethod(useGet: Boolean) {
        prefs.edit().putBoolean("use_get", useGet).apply()
    }

    fun loadMethod(): Boolean = prefs.getBoolean("use_get", false)

    private fun getDefaultGroups(): MutableList<UrlGroup> = mutableListOf(
        UrlGroup(
            id = UUID.randomUUID().toString(),
            name = "Белый список",
            urls = mutableListOf(
                UrlEntry(url = "https://ya.ru"),
                UrlEntry(url = "https://max.ru"),
                UrlEntry(url = "https://rzd.ru"),
                UrlEntry(url = "https://dzen.ru"),
                UrlEntry(url = "https://gosuslugi.ru"),

            )
        ),
        UrlGroup(
            id = UUID.randomUUID().toString(),
            name = "Незаблокированные ресурсы",
            urls = mutableListOf(
                UrlEntry(url = "https://google.com"),
                UrlEntry(url = "https://apple.com"),
                UrlEntry(url = "https://microsoft.com"),
                UrlEntry(url = "https://wikipedia.org"),
                UrlEntry(url = "https://store.steampowered.com"),
            )
        ),
        UrlGroup(
            id = UUID.randomUUID().toString(),
            name = "Заблокированные ресурсы",
            urls = mutableListOf(
                UrlEntry(url = "https://telegram.org"),
                UrlEntry(url = "https://youtube.com"),
                UrlEntry(url = "https://discord.com"),
                UrlEntry(url = "https://facebook.com"),
                UrlEntry(url = "https://instagram.com"),
            )
        )
    )
}
