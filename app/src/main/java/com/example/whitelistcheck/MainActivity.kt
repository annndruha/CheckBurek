package com.example.whitelistcheck

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MainActivity : AppCompatActivity() {

    companion object {
        const val ATTEMPTS        = 3
        const val CONCURRENT_MAX  = 5  // max parallel domain checks
    }

    private lateinit var storage: StorageManager
    private lateinit var adapter: NetworkListAdapter
    private val checker  = NetworkChecker()
    private val semaphore = Semaphore(CONCURRENT_MAX)
    private var groups: MutableList<UrlGroup> = mutableListOf()
    private var selectedMethod: String = NetworkChecker.METHOD_HEAD

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        window.attributes = window.attributes.also { it.screenBrightness = -1f }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = StorageManager(this)
        groups  = storage.loadGroups()
        selectedMethod = storage.loadMethod()

        adapter = NetworkListAdapter(
            onAddGroup      = ::showAddGroupDialog,
            onDeleteGroup   = ::deleteGroup,
            onRenameGroup   = ::renameGroup,
            onAddUrl        = ::showAddUrlDialog,
            onDeleteUrl     = ::deleteUrl,
            onCheckGroup    = ::checkGroup,
            onMethodChanged = { method ->
                selectedMethod = method
                storage.saveMethod(method)
            },
        )
        adapter.currentMethod = selectedMethod

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
        val et = EditText(this).apply {
            hint = "Название списка"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Новый список")
            .setView(makeDialogContainer(et))
            .setPositiveButton("Добавить") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) { groups.add(UrlGroup(name = name)); persist() }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteGroup(group: UrlGroup) { groups.remove(group); persist() }

    private fun renameGroup(group: UrlGroup, name: String) { group.name = name; persist() }

    // ──────────────────────────────────────────────
    //  URL management
    // ──────────────────────────────────────────────

    private fun showAddUrlDialog(group: UrlGroup) {
        val et = EditText(this).apply {
            hint = "example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить домен")
            .setView(makeDialogContainer(et))
            .setPositiveButton("Добавить") { _, _ ->
                var url = et.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
                    group.urls.add(UrlEntry(url = url))
                    persist()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun makeDialogContainer(et: EditText): android.widget.FrameLayout {
        val dp20 = (20 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        return android.widget.FrameLayout(this).also {
            val lp = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp20, dp12, dp20, 0) }
            it.addView(et, lp)
        }
    }

    private fun deleteUrl(group: UrlGroup, entry: UrlEntry) { group.urls.remove(entry); persist() }

    // ──────────────────────────────────────────────
    //  Network checking
    // ──────────────────────────────────────────────

    private fun checkGroup(group: UrlGroup) {
        val method = selectedMethod
        scope.launch {
            group.urls.map { entry ->
                async(Dispatchers.IO) {
                    // 1. Show queued state immediately
                    entry.state = CheckState.Queued
                    withContext(Dispatchers.Main) { adapter.updateEntry(entry) }

                    // 2. Wait for a slot in the semaphore, then run
                    semaphore.withPermit {
                        val finalState = checker.check(
                            url     = entry.url,
                            method  = method,
                            attempts = ATTEMPTS,
                            onAttempt = { attempt ->
                                entry.state = CheckState.Checking(attempt, ATTEMPTS)
                                withContext(Dispatchers.Main) { adapter.updateEntry(entry) }
                            }
                        )
                        entry.state = finalState
                        withContext(Dispatchers.Main) { adapter.updateEntry(entry) }
                    }
                }
            }.awaitAll()
        }
    }

    private fun checkAll() { groups.forEach { checkGroup(it) } }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private fun persist() {
        adapter.submitGroups(groups)
        storage.saveGroups(groups)
    }
}
