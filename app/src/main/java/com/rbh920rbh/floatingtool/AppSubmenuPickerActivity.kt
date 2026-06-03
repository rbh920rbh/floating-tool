package com.rbh920rbh.floatingtool

import android.app.role.RoleManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * 两步：先选应用，再选该应用在桌面长按菜单中暴露的快捷方式。
 * 优先 [LauncherApps]（需系统授权）；否则解析 APK 内 shortcuts.xml。
 */
class AppSubmenuPickerActivity : AppCompatActivity() {

    private lateinit var adapter: RowAdapter
    private var appEntries: List<AppPickerActivity.LauncherEntry> = emptyList()
    private var shortcutEntries: List<ShortcutEntry> = emptyList()
    private var showingShortcuts = false
    private var hasShortcutHostPermission = false

    private val requestHomeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshShortcutAccessState()
        Toast.makeText(this, R.string.submenu_role_result, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Toast.makeText(this, R.string.error_submenu_api, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        ensureShortcutAccessPermission()

        refreshShortcutAccessState()

        findViewById<MaterialButton>(R.id.btn_submenu_full_access).apply {
            visibility = View.VISIBLE
            setOnClickListener { requestFullShortcutAccess() }
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

    private fun refreshShortcutAccessState() {
        val launcherApps = getSystemService(LauncherApps::class.java)
        hasShortcutHostPermission = launcherApps?.hasShortcutHostPermission() == true
    }

    private fun requestFullShortcutAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                requestHomeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        Toast.makeText(this, R.string.submenu_role_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun showAppList() {
        showingShortcuts = false
        refreshShortcutAccessState()
        findViewById<View>(R.id.tv_empty_state).visibility = View.GONE
        findViewById<RecyclerView>(R.id.recycler_apps).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_picker_title).text = getString(R.string.picker_submenu_pick_app)
        val hint = if (hasShortcutHostPermission) {
            getString(R.string.submenu_source_launcher, appEntries.size)
        } else {
            getString(R.string.submenu_source_access_shortcuts, appEntries.size)
        }
        findViewById<TextView>(R.id.tv_picker_subtitle).text = hint
        findViewById<MaterialButton>(R.id.btn_submenu_full_access).visibility =
            if (hasShortcutHostPermission) View.GONE else View.VISIBLE
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

        // 与桌面相同 API：LauncherApps（含微信等动态长按菜单）
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
        val queryFlags = buildShortcutQueryFlags()
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(queryFlags)
        }
        val shortcuts = try {
            launcherApps.getShortcuts(query, user) ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "getShortcuts denied for $packageName", e)
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getShortcuts failed for $packageName", e)
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
                launchIntentUri = null,
                icon = loadShortcutIcon(launcherApps, shortcut, user),
            )
        }
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

    private fun buildShortcutQueryFlags(): Int {
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
        }
        return flags
    }

    companion object {
        private const val TAG = "AppSubmenuPicker"
        /** API 30+，部分 compileSdk 未导出 Manifest.permission 常量 */
        private const val PERMISSION_ACCESS_SHORTCUTS = "android.permission.ACCESS_SHORTCUTS"
        private const val REQUEST_ACCESS_SHORTCUTS = 2002
    }
}
