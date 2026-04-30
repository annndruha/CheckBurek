package com.example.whitelistcheck

import java.util.UUID

data class UrlGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val urls: MutableList<UrlEntry> = mutableListOf()
)

class UrlEntry(
    val id: String = UUID.randomUUID().toString(),
    var url: String
) {
    @Transient
    var state: CheckState? = null
}

sealed class CheckState {
    object Idle : CheckState()
    object Checking : CheckState()
    data class Success(val code: Int, val timeMs: Long) : CheckState()
    data class Failure(val code: Int?, val error: String, val timeMs: Long) : CheckState()
}
