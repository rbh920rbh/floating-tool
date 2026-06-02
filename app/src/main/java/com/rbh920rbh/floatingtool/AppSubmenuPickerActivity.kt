package com.rbh920rbh.floatingtool

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 两步：先选应用，再选该应用在桌面长按菜单中暴露的快捷方式（LauncherApps）。
 */
class AppSubmenuPickerActivity : AppCompatActivity() {

    private lateinit var adapter: RowAdapter
    private var appEntries: List<AppPickerActivity.LauncherEntry> = emptyList()
    private var shortcutEntries: List<ShortcutEntry> = emptyList()
    private var selectedPackage: String? = null
    private var showingShortcuts = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Toast.makeText(this, R.string.error_submenu_api, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        adapter = RowAdapter(emptyList()) { entry -> onRowClicked(entry) }
        findViewById<RecyclerView>(R.id.recycler_apps).apply {
            layoutManager = LinearLayoutManager(this@AppSubmenuPickerActivity)
            this.adapter = this@AppSubmenuPickerActivity.adapter
        }

        findViewById<EditText>(R.id.search_apps).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterRows(s?.toString().orEmpty())
            }
        })

        appEntries = loadLauncherApps()
        if (appEntries.isEmpty()) {
            Toast.makeText(this, R.string.error_no_launcher_apps, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        showAppList()
    }

    override fun onBackPressed() {
        if (showingShortcuts) {
            showAppList()
            return
        }
        super.onBackPressed()
    }

    private fun showAppList() {
        showingShortcuts = false
        selectedPackage = null
        findViewById<TextView>(R.id.tv_picker_title).text = getString(R.string.picker_submenu_pick_app)
        findViewById<TextView>(R.id.tv_picker_subtitle).text =
            getString(R.string.picker_app_count, appEntries.size)
        adapter.submitApps(appEntries)
    }

    private fun showShortcutList(packageName: String, appLabel: String) {
        showingShortcuts = true
        selectedPackage = packageName
        shortcutEntries = loadShortcuts(packageName)
        findViewById<TextView>(R.id.tv_picker_title).text =
            getString(R.string.picker_submenu_pick_item, appLabel)
        if (shortcutEntries.isEmpty()) {
            findViewById<TextView>(R.id.tv_picker_subtitle).text =
                getString(R.string.error_no_submenu_items)
            Toast.makeText(this, R.string.error_no_submenu_items, Toast.LENGTH_LONG).show()
        } else {
            findViewById<TextView>(R.id.tv_picker_subtitle).text =
                getString(R.string.picker_submenu_count, shortcutEntries.size)
        }
        adapter.submitShortcuts(shortcutEntries)
    }

    private fun onRowClicked(entry: RowEntry) {
        when (entry) {
            is RowEntry.App -> showShortcutList(entry.data.packageName, entry.data.label)
            is RowEntry.Shortcut -> {
                OverlayItemStore.add(
                    this,
                    OverlayItem.AppSubmenuShortcut(
                        id = newOverlayItemId(),
                        packageName = entry.data.packageName,
                        shortcutId = entry.data.shortcutId,
                        label = entry.data.label,
                    ),
                )
                sendItemsChanged()
                finish()
            }
        }
    }

    private fun filterRows(query: String) {
        val trimmed = query.trim()
        if (!showingShortcuts) {
            val list = if (trimmed.isEmpty()) {
                appEntries
            } else {
                val lower = trimmed.lowercase()
                appEntries.filter {
                    it.label.lowercase().contains(lower) || it.packageName.lowercase().contains(lower)
                }
            }
            adapter.submitApps(list)
            return
        }
        val list = if (trimmed.isEmpty()) {
            shortcutEntries
        } else {
            val lower = trimmed.lowercase()
            shortcutEntries.filter { it.label.lowercase().contains(lower) }
        }
        adapter.submitShortcuts(list)
    }

    private fun loadLauncherApps(): List<AppPickerActivity.LauncherEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val flags = PackageManager.MATCH_ALL
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
            AppPickerActivity.LauncherEntry(pkg, label, info.loadIcon(packageManager))
        }.sortedBy { it.label.lowercase() }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun loadShortcuts(packageName: String): List<ShortcutEntry> {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return emptyList()
        val user = Process.myUserHandle()
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        }
        val shortcuts = try {
            launcherApps.getShortcuts(query, user) ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
        return shortcuts.mapNotNull { shortcut ->
            if (!shortcut.isEnabled) return@mapNotNull null
            val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
                ?: shortcut.id
            ShortcutEntry(
                packageName = packageName,
                shortcutId = shortcut.id,
                label = label,
                icon = loadShortcutIcon(launcherApps, shortcut, user),
            )
        }.sortedBy { it.label.lowercase() }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun loadShortcutIcon(
        launcherApps: LauncherApps,
        shortcut: ShortcutInfo,
        user: android.os.UserHandle,
    ): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.densityDpi
        return try {
            launcherApps.getShortcutIconDrawable(shortcut, density)
        } catch (_: Exception) {
            null
        } ?: try {
            packageManager.getApplicationIcon(shortcut.`package`)
        } catch (_: Exception) {
            ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)
        }!!
    }

    private fun sendItemsChanged() {
        sendBroadcast(Intent(AppPickerActivity.ACTION_OVERLAY_ITEMS_CHANGED).setPackage(packageName))
    }

    data class ShortcutEntry(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable,
    )

    private sealed class RowEntry {
        data class App(val data: AppPickerActivity.LauncherEntry) : RowEntry()
        data class Shortcut(val data: ShortcutEntry) : RowEntry()
    }

    private class RowAdapter(
        private var rows: List<RowEntry>,
        private val onClick: (RowEntry) -> Unit,
    ) : RecyclerView.Adapter<RowAdapter.Holder>() {

        fun submitApps(apps: List<AppPickerActivity.LauncherEntry>) {
            rows = apps.map { RowEntry.App(it) }
            notifyDataSetChanged()
        }

        fun submitShortcuts(shortcuts: List<ShortcutEntry>) {
            rows = shortcuts.map { RowEntry.Shortcut(it) }
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
            when (val row = rows[position]) {
                is RowEntry.App -> {
                    holder.icon.setImageDrawable(row.data.icon)
                    holder.label.text = row.data.label
                }
                is RowEntry.Shortcut -> {
                    holder.icon.setImageDrawable(row.data.icon)
                    holder.label.text = row.data.label
                }
            }
            holder.itemView.setOnClickListener { onClick(rows[position]) }
        }

        override fun getItemCount(): Int = rows.size
    }
}
