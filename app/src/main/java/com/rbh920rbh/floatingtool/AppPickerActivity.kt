package com.rbh920rbh.floatingtool

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppPickerActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private var allApps: List<LauncherEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        allApps = loadLauncherApps()
        val activity = this
        adapter = AppAdapter(emptyList()) { entry ->
            OverlayItemStore.add(
                activity,
                OverlayItem.AppShortcut(
                    id = newOverlayItemId(),
                    packageName = entry.packageName,
                    label = entry.label,
                ),
            )
            sendItemsChanged()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        }

        findViewById<RecyclerView>(R.id.recycler_apps).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = this@AppPickerActivity.adapter
        }

        findViewById<EditText>(R.id.search_apps).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString().orEmpty())
            }
        })

        if (allApps.isEmpty()) {
            Toast.makeText(this, R.string.error_no_launcher_apps, Toast.LENGTH_LONG).show()
        } else {
            adapter.submit(allApps)
            findViewById<TextView>(R.id.tv_picker_subtitle).text =
                getString(R.string.picker_app_count, allApps.size)
        }
    }

    private fun filterApps(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            adapter.submit(allApps)
            return
        }
        val lower = trimmed.lowercase()
        adapter.submit(
            allApps.filter {
                it.label.lowercase().contains(lower) || it.packageName.lowercase().contains(lower)
            },
        )
    }

    private fun loadLauncherApps(): List<LauncherEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val flags = PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, flags)
        }

        val seen = linkedSetOf<String>()
        return activities.mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            if (!seen.add(pkg)) return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: pkg
            LauncherEntry(pkg, label, info.loadIcon(packageManager))
        }.sortedBy { it.label.lowercase() }
    }

    private fun sendItemsChanged() {
        sendBroadcast(Intent(ACTION_OVERLAY_ITEMS_CHANGED).setPackage(packageName))
    }

    data class LauncherEntry(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable,
    )

    private class AppAdapter(
        private var items: List<LauncherEntry>,
        private val onClick: (LauncherEntry) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.Holder>() {

        fun submit(newItems: List<LauncherEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val label: TextView = view.findViewById(R.id.app_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_picker_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val ACTION_OVERLAY_ITEMS_CHANGED = "com.rbh920rbh.floatingtool.OVERLAY_ITEMS_CHANGED"
    }
}
