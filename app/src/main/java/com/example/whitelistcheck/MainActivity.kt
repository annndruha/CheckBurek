package com.example.whitelistcheck

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var storage: StorageManager
    private lateinit var adapter: NetworkListAdapter
    private val checker = NetworkChecker()
    private var groups: MutableList<UrlGroup> = mutableListOf()

    // SupervisorJob so one failing coroutine doesn't cancel others
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        window.attributes = window.attributes.also {
            it.screenBrightness = -1f
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = StorageManager(this)
        groups = storage.loadGroups()

        adapter = NetworkListAdapter(
            onAddGroup   = ::showAddGroupDialog,
            onDeleteGroup = ::deleteGroup,
            onRenameGroup = ::renameGroup,
            onAddUrl     = ::showAddUrlDialog,
            onDeleteUrl  = ::deleteUrl,
            onCheckGroup = ::checkGroup,
        )

        findViewById<RecyclerView>(R.id.rvMain).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        adapter.submitGroups(groups)

        findViewById<ExtendedFloatingActionButton>(R.id.fabCheckAll)
            .setOnClickListener { checkAll() }
    }

    override fun onStop() {
        super.onStop()
        // Persist any changes (also saved on every edit, but just in case)
        storage.saveGroups(groups)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ──────────────────────────────────────────────
    //  Group management
    // ──────────────────────────────────────────────

    private fun showAddGroupDialog() {
        val et = EditText(this).apply { hint = "Название списка" }
        val container = android.widget.FrameLayout(this).also {
            val dp20 = (20 * resources.displayMetrics.density).toInt()
            val dp8  = (8  * resources.displayMetrics.density).toInt()
            val lp = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp20, dp8, dp20, 0) }
            it.addView(et, lp)
        }

        AlertDialog.Builder(this)
            .setTitle("Новый список")
            .setView(container)
            .setPositiveButton("Добавить") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    groups.add(UrlGroup(name = name))
                    persist()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteGroup(group: UrlGroup) {
        groups.remove(group)
        persist()
    }

    private fun renameGroup(group: UrlGroup, name: String) {
        group.name = name
        persist()
    }

    // ──────────────────────────────────────────────
    //  URL management
    // ──────────────────────────────────────────────

    private fun showAddUrlDialog(group: UrlGroup) {
        val et = EditText(this).apply { hint = "example.com" }
        val container = android.widget.FrameLayout(this).also {
            val dp20 = (20 * resources.displayMetrics.density).toInt()
            val dp8  = (8  * resources.displayMetrics.density).toInt()
            val lp = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp20, dp8, dp20, 0) }
            it.addView(et, lp)
        }
        AlertDialog.Builder(this)
            .setTitle("Добавить домен")
            .setView(container)
            .setPositiveButton("Добавить") { _, _ ->
                var url = et.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    group.urls.add(UrlEntry(url = url))
                    persist()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteUrl(group: UrlGroup, entry: UrlEntry) {
        group.urls.remove(entry)
        persist()
    }

    // ──────────────────────────────────────────────
    //  Network checking
    // ──────────────────────────────────────────────

    private fun checkGroup(group: UrlGroup) {
        scope.launch {
            group.urls.map { entry ->
                async(Dispatchers.IO) {
                    entry.state = CheckState.Checking
                    withContext(Dispatchers.Main) { adapter.updateEntry(entry) }

                    entry.state = checker.check(entry.url)
                    withContext(Dispatchers.Main) { adapter.updateEntry(entry) }
                }
            }.awaitAll()
        }
    }

    private fun checkAll() {
        groups.forEach { checkGroup(it) }
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private fun persist() {
        adapter.submitGroups(groups)
        storage.saveGroups(groups)
    }
}
