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
import android.util.Log
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
 * 先选应用，再选长按菜单项。
 * 通过 [LauncherApps.getShortcuts] + ACCESS_SHORTCUTS 读取（与常见工具类应用相同，无需设为默认桌面）。
 */
class AppSubmenuPickerActivity : AppCompatActivity() {

    private lateinit var adapter: RowAdapter
    private var appEntries: List<AppPickerActivity.LauncherEntry> = emptyList()
    private var shortcutEntries: List<ShortcutEntry> = emptyList()
    private var showingShortcuts = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Toast.makeText(this, R.string.error_submenu_api, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, R.string.submenu_api30_hint, Toast.LENGTH_LONG).show()
        } else {
            ensureShortcutAccessPermission()
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

    private fun ensureShortcutAccessPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (ContextCompat.checkSelfPermission(this, PERMISSION_ACCESS_SHORTCUTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(PERMISSION_ACCESS_SHORTCUTS), REQUEST_ACCESS_SHORTCUTS)
    }

    private fun showAppList() {
        showingShortcuts = false
        findViewById<View>(R.id.tv_empty_state).visibility = View.GONE
        findViewById<RecyclerView>(R.id.recycler_apps).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_picker_title).text = getString(R.string.picker_submenu_pick_app)
        findViewById<TextView>(R.id.tv_picker_subtitle).text =
            getString(R.string.submenu_source_access_shortcuts, appEntries.size)
        adapter.submitApps(appEntries)
    }

    private fun showShortcutList(packageName: String, appLabel: String) {
        showingShortcuts = true
        shortcutEntries = loadShortcuts(packageName)
        findViewById<TextView>(R.id.tv_picker_title).text =
            getString(R.string.picker_submenu_pick_item, appLabel)
        val emptyView = findViewById<TextView>(R.id.tv_empty_state)
        val listView = findViewById<RecyclerView>(R.id.recycler_apps)
        if (shortcutEntries.isEmpty()) {
            findViewById<TextView>(R.id.tv_picker_subtitle).text =
                getString(R.string.error_no_submenu_items)
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.error_no_submenu_items_detail)
        } else {
            findViewById<TextView>(R.id.tv_picker_subtitle).text =
                getString(R.string.picker_submenu_count, shortcutEntries.size)
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.submitShortcuts(shortcutEntries)
        }
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
                        launchIntentUri = entry.data.launchIntentUri,
                    ),
                )
                sendItemsChanged()
                closePicker()
            }
        }
    }

    private fun closePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        if (showingShortcuts) {
            showAppList()
            return
        }
        closePicker()
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
        val merged = linkedMapOf<String, ShortcutEntry>()

        loadLauncherShortcuts(packageName).forEach { merged[it.shortcutId] = it }

        ManifestShortcutParser.loadStaticShortcuts(packageManager, packageName).forEach { parsed ->
            merged.putIfAbsent(
                parsed.shortcutId,
                ShortcutEntry(
                    packageName = packageName,
                    shortcutId = parsed.shortcutId,
                    label = parsed.label,
                    launchIntentUri = parsed.launchIntentUri,
                    icon = loadAppIcon(packageName),
                ),
            )
        }

        return merged.values.sortedBy { it.label.lowercase() }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun loadLauncherShortcuts(packageName: String): List<ShortcutEntry> {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return emptyList()
        val user = Process.myUserHandle()
        val merged = linkedMapOf<String, ShortcutEntry>()

        for (flags in shortcutQueryFlagSets()) {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(flags)
            }
            val shortcuts = try {
                launcherApps.getShortcuts(query, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "getShortcuts denied pkg=$packageName flags=$flags", e)
                null
            } catch (e: Exception) {
                Log.w(TAG, "getShortcuts failed pkg=$packageName flags=$flags", e)
                null
            } ?: continue

            for (shortcut in shortcuts) {
                if (!shortcut.isEnabled) continue
                val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: shortcut.id
                merged.putIfAbsent(
                    shortcut.id,
                    ShortcutEntry(
                        packageName = packageName,
                        shortcutId = shortcut.id,
                        label = label,
                        launchIntentUri = extractLaunchUri(shortcut),
                        icon = loadShortcutIcon(launcherApps, shortcut, user),
                    ),
                )
            }
        }

        return merged.values.toList()
    }

    private fun shortcutQueryFlagSets(): List<Int> {
        val list = mutableListOf(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED,
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER)
            list.add(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER,
            )
        }
        return list.distinct()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun extractLaunchUri(shortcut: ShortcutInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intents = shortcut.intents
            if (!intents.isNullOrEmpty()) {
                return intents.first().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }.toUri(Intent.URI_INTENT_SCHEME)
            }
        }
        @Suppress("DEPRECATION")
        val legacy = shortcut.intent
        if (legacy != null) {
            return legacy.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                .toUri(Intent.URI_INTENT_SCHEME)
        }
        return null
    }

    private fun loadAppIcon(packageName: String): android.graphics.drawable.Drawable {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
        }
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
        } ?: loadAppIcon(shortcut.`package`)
    }

    private fun sendItemsChanged() {
        sendBroadcast(Intent(AppPickerActivity.ACTION_OVERLAY_ITEMS_CHANGED).setPackage(packageName))
    }

    data class ShortcutEntry(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val launchIntentUri: String?,
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

    companion object {
        private const val TAG = "AppSubmenuPicker"
        private const val PERMISSION_ACCESS_SHORTCUTS = "android.permission.ACCESS_SHORTCUTS"
        private const val REQUEST_ACCESS_SHORTCUTS = 2002
    }
}
