package com.rbh920rbh.floatingtool

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        val apps = loadLauncherApps()
        findViewById<RecyclerView>(R.id.recycler_apps).apply {
            layoutManager = LinearLayoutManager(this@AppPickerActivity)
            adapter = AppAdapter(apps) { entry ->
                OverlayItemStore.add(
                    this,
                    OverlayItem.AppShortcut(
                        id = newOverlayItemId(),
                        packageName = entry.packageName,
                        label = entry.label,
                    ),
                )
                sendItemsChanged()
                finish()
            }
        }
    }

    private fun loadLauncherApps(): List<LauncherEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
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
        private val items: List<LauncherEntry>,
        private val onClick: (LauncherEntry) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.Holder>() {

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
