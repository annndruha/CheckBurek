package com.example.whitelistcheck

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NetworkListAdapter(
    private val onAddGroup: () -> Unit,
    private val onDeleteGroup: (UrlGroup) -> Unit,
    private val onRenameGroup: (UrlGroup, String) -> Unit,
    private val onAddUrl: (UrlGroup) -> Unit,
    private val onDeleteUrl: (UrlGroup, UrlEntry) -> Unit,
    private val onCheckGroup: (UrlGroup) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_GROUP_HEADER = 0
        const val TYPE_URL_ITEM = 1
        const val TYPE_ADD_URL = 2
        const val TYPE_ADD_GROUP = 3
    }

    sealed class ListItem {
        data class GroupHeader(val group: UrlGroup) : ListItem()
        data class UrlItem(val group: UrlGroup, val entry: UrlEntry) : ListItem()
        data class AddUrl(val group: UrlGroup) : ListItem()
        object AddGroup : ListItem()
    }

    private val items = mutableListOf<ListItem>()

    fun submitGroups(groups: List<UrlGroup>) {
        items.clear()
        for (group in groups) {
            items.add(ListItem.GroupHeader(group))
            for (entry in group.urls) {
                items.add(ListItem.UrlItem(group, entry))
            }
            items.add(ListItem.AddUrl(group))
        }
        items.add(ListItem.AddGroup)
        notifyDataSetChanged()
    }

    fun updateEntry(entry: UrlEntry) {
        val index = items.indexOfFirst { it is ListItem.UrlItem && it.entry.id == entry.id }
        if (index >= 0) notifyItemChanged(index, "state_change")
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.GroupHeader -> TYPE_GROUP_HEADER
        is ListItem.UrlItem    -> TYPE_URL_ITEM
        is ListItem.AddUrl     -> TYPE_ADD_URL
        is ListItem.AddGroup   -> TYPE_ADD_GROUP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_GROUP_HEADER -> GroupHeaderVH(inf.inflate(R.layout.item_group_header, parent, false))
            TYPE_URL_ITEM     -> UrlItemVH(inf.inflate(R.layout.item_url, parent, false))
            TYPE_ADD_URL      -> AddUrlVH(inf.inflate(R.layout.item_add_url, parent, false))
            else              -> AddGroupVH(inf.inflate(R.layout.item_add_group, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.GroupHeader -> (holder as GroupHeaderVH).bind(item.group)
            is ListItem.UrlItem    -> (holder as UrlItemVH).bind(item.group, item.entry)
            is ListItem.AddUrl     -> (holder as AddUrlVH).bind(item.group)
            is ListItem.AddGroup   -> (holder as AddGroupVH).bind()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && holder is UrlItemVH) {
            val item = items[position] as? ListItem.UrlItem ?: return
            holder.bindState(item.entry)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ──────────────────────────────────────────────
    //  VIEW HOLDERS
    // ──────────────────────────────────────────────

    inner class GroupHeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvName: TextView      = v.findViewById(R.id.tvGroupName)
        private val btnCheck: ImageButton = v.findViewById(R.id.btnCheckGroup)
        private val btnEdit: ImageButton  = v.findViewById(R.id.btnEditGroup)
        private val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteGroup)

        fun bind(group: UrlGroup) {
            tvName.text = group.name

            btnCheck.setOnClickListener { onCheckGroup(group) }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(itemView.context)
                    .setTitle("Удалить список?")
                    .setMessage("«${group.name}» будет удалён безвозвратно.")
                    .setPositiveButton("Удалить") { _, _ -> onDeleteGroup(group) }
                    .setNegativeButton("Отмена", null)
                    .show()
            }

            btnEdit.setOnClickListener {
                val et = EditText(itemView.context).apply { setText(group.name) }
                val dp20 = (20 * itemView.context.resources.displayMetrics.density).toInt()
                val dp8  = (8  * itemView.context.resources.displayMetrics.density).toInt()
                val container = android.widget.FrameLayout(itemView.context).also {
                    val lp = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(dp20, dp8, dp20, 0) }
                    it.addView(et, lp)
                }
                AlertDialog.Builder(itemView.context)
                    .setTitle("Переименовать список")
                    .setView(container)
                    .setPositiveButton("Сохранить") { _, _ ->
                        val name = et.text.toString().trim()
                        if (name.isNotEmpty()) onRenameGroup(group, name)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
    }

    inner class UrlItemVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvUrl: TextView       = v.findViewById(R.id.tvUrl)
        private val tvStatus: TextView    = v.findViewById(R.id.tvStatus)
        private val tvMeta: TextView      = v.findViewById(R.id.tvMeta)
        private val tvError: TextView     = v.findViewById(R.id.tvError)
        private val progress: ProgressBar = v.findViewById(R.id.progressUrl)
        private val btnDelete: Button     = v.findViewById(R.id.btnDeleteUrl)

        // Resolve colors from resources once
        private val colorSuccess = v.context.getColor(R.color.md_success)
        private val colorError   = v.context.getColor(R.color.md_error)
        private val colorIdle    = v.context.getColor(R.color.md_idle)

        fun bind(group: UrlGroup, entry: UrlEntry) {
            tvUrl.text = entry.url
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
            btnDelete.setOnClickListener { onDeleteUrl(group, entry) }
            bindState(entry)
        }

        fun bindState(entry: UrlEntry) {
            itemView.setOnClickListener(null)
            itemView.isClickable = false

            when (val s = entry.state ?: CheckState.Idle) {
                is CheckState.Idle -> {
                    progress.visibility = View.GONE
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "–"
                    tvStatus.setTextColor(colorIdle)
                    tvMeta.visibility = View.GONE
                    tvError.visibility = View.GONE
                }
                is CheckState.Checking -> {
                    progress.visibility = View.VISIBLE
                    tvStatus.visibility = View.GONE
                    tvMeta.visibility = View.GONE
                    tvError.visibility = View.GONE
                }
                is CheckState.Success -> {
                    progress.visibility = View.GONE
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "✓"
                    tvStatus.setTextColor(colorSuccess)
                    tvMeta.visibility = View.VISIBLE
                    tvMeta.text = "${s.code} · ${s.timeMs} мс"
                    tvError.visibility = View.GONE
                }
                is CheckState.Failure -> {
                    progress.visibility = View.GONE
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "✗"
                    tvStatus.setTextColor(colorError)
                    tvMeta.visibility = View.VISIBLE
                    val codeStr = s.code?.toString() ?: "–"
                    tvMeta.text = "$codeStr · ${s.timeMs} мс  ▾ подробнее"
                    tvError.visibility = View.GONE
                    tvError.text = s.error
                    itemView.isClickable = true
                    itemView.setOnClickListener {
                        tvError.visibility =
                            if (tvError.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    inner class AddUrlVH(v: View) : RecyclerView.ViewHolder(v) {
        private val btn: Button = v.findViewById(R.id.btnAddUrl)
        fun bind(group: UrlGroup) { btn.setOnClickListener { onAddUrl(group) } }
    }

    inner class AddGroupVH(v: View) : RecyclerView.ViewHolder(v) {
        private val btn: Button = v.findViewById(R.id.btnAddGroup)
        fun bind() { btn.setOnClickListener { onAddGroup() } }
    }
}
