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

    private fun getDefaultGroups(): MutableList<UrlGroup> = mutableListOf(
        UrlGroup(
            id = UUID.randomUUID().toString(),
            name = "Белый список",
            urls = mutableListOf(
                UrlEntry(url = "https://ya.ru"),
                UrlEntry(url = "https://dzen.ru"),
                UrlEntry(url = "https://vk.com"),
                UrlEntry(url = "https://sberbank.ru"),
                UrlEntry(url = "https://2gis.ru"),
                UrlEntry(url = "https://max.ru"),
            )
        ),
        UrlGroup(
            id = UUID.randomUUID().toString(),
            name = "Незаблокированные ресурсы",
            urls = mutableListOf(
                UrlEntry(url = "https://store.steampowered.com"),
                UrlEntry(url = "https://habr.com"),
                UrlEntry(url = "https://ru.wikipedia.org"),
            )
        ),
        UrlGroup(
            id = UUID.randomUUID().toString(),
            name = "Заблокированные ресурсы",
            urls = mutableListOf(
                UrlEntry(url = "https://twitter.com"),
                UrlEntry(url = "https://instagram.com"),
                UrlEntry(url = "https://web.telegram.org"),
            )
        )
    )
}
