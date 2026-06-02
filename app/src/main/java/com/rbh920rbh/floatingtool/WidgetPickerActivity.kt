package com.rbh920rbh.floatingtool

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 使用 [AppWidgetManager.getInstalledProviders] 自建列表，不依赖系统 APPWIDGET_PICK。
 */
class WidgetPickerActivity : AppCompatActivity() {

    private lateinit var widgetHost: AppWidgetHost
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: ComponentName? = null
    private lateinit var adapter: WidgetAdapter
    private var allProviders: List<WidgetProviderEntry> = emptyList()

    private val bindWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> commitPendingWidget()
            else -> {
                toastAndFinish(R.string.widget_bind_denied)
            }
        }
    }

    private val configureWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> commitPendingWidget()
            else -> {
                releasePendingWidget()
                toastAndFinish(R.string.widget_pick_cancelled)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_picker)

        widgetHost = AppWidgetHost(this, OverlayPanelView.APP_WIDGET_HOST_ID)
        allProviders = loadInstalledWidgetProviders()
        adapter = WidgetAdapter(emptyList()) { entry -> onProviderSelected(entry.info) }

        findViewById<RecyclerView>(R.id.recycler_widgets).apply {
            layoutManager = LinearLayoutManager(this@WidgetPickerActivity)
            this.adapter = this@WidgetPickerActivity.adapter
        }

        findViewById<EditText>(R.id.search_widgets).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterProviders(s?.toString().orEmpty())
            }
        })

        if (allProviders.isEmpty()) {
            Toast.makeText(this, R.string.error_no_widgets, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        adapter.submit(allProviders)
        findViewById<TextView>(R.id.tv_widget_picker_subtitle).text =
            getString(R.string.picker_widget_count, allProviders.size)
    }

    private fun loadInstalledWidgetProviders(): List<WidgetProviderEntry> {
        val manager = AppWidgetManager.getInstance(this)
        val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getInstalledProviders(PackageManager.GET_META_DATA)
        } else {
            @Suppress("DEPRECATION")
            manager.installedProviders
        }
        return providers.mapNotNull { info ->
            val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: info.provider.packageName
            val appLabel = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(info.provider.packageName, 0),
                ).toString()
            } catch (_: Exception) {
                info.provider.packageName
            }
            WidgetProviderEntry(
                info = info,
                label = label,
                appLabel = appLabel,
                icon = info.loadIcon(this, packageManager),
            )
        }.sortedBy { "${it.appLabel}/${it.label}".lowercase() }
    }

    private fun filterProviders(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            adapter.submit(allProviders)
            return
        }
        val lower = trimmed.lowercase()
        adapter.submit(
            allProviders.filter {
                it.label.lowercase().contains(lower) ||
                    it.appLabel.lowercase().contains(lower) ||
                    it.info.provider.packageName.lowercase().contains(lower)
            },
        )
    }

    private fun onProviderSelected(info: AppWidgetProviderInfo) {
        releasePendingWidget()
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        pendingProvider = info.provider

        val manager = AppWidgetManager.getInstance(this)
        if (!manager.bindAppWidgetIdIfAllowed(pendingWidgetId, info.provider)) {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            if (bindIntent.resolveActivity(packageManager) != null) {
                try {
                    bindWidget.launch(bindIntent)
                    return
                } catch (_: Exception) {
                }
            }
            Toast.makeText(this, R.string.widget_bind_denied, Toast.LENGTH_LONG).show()
            releasePendingWidget()
            return
        }

        launchConfigureIfNeeded(info)
    }

    private fun launchConfigureIfNeeded(info: AppWidgetProviderInfo) {
        val configure = info.configure
        if (configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            }
            if (configIntent.resolveActivity(packageManager) != null) {
                try {
                    configureWidget.launch(configIntent)
                    return
                } catch (_: Exception) {
                }
            }
        }
        commitPendingWidget()
    }

    private fun commitPendingWidget() {
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        if (manager.getAppWidgetInfo(pendingWidgetId) == null) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
            releasePendingWidget()
            finish()
            return
        }
        OverlayItemStore.load(this)
        OverlayItemStore.add(
            this,
            OverlayItem.WidgetSlot(id = newOverlayItemId(), appWidgetId = pendingWidgetId),
        )
        sendBroadcast(
            Intent(AppPickerActivity.ACTION_OVERLAY_ITEMS_CHANGED).setPackage(packageName),
        )
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
        Toast.makeText(this, R.string.widget_added, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun releasePendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && ::widgetHost.isInitialized) {
            try {
                widgetHost.deleteAppWidgetId(pendingWidgetId)
            } catch (_: Exception) {
            }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
    }

    private fun toastAndFinish(messageRes: Int) {
        releasePendingWidget()
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        finish()
    }

    data class WidgetProviderEntry(
        val info: AppWidgetProviderInfo,
        val label: String,
        val appLabel: String,
        val icon: android.graphics.drawable.Drawable,
    )

    private class WidgetAdapter(
        private var items: List<WidgetProviderEntry>,
        private val onClick: (WidgetProviderEntry) -> Unit,
    ) : RecyclerView.Adapter<WidgetAdapter.Holder>() {

        fun submit(newItems: List<WidgetProviderEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val label: TextView = view.findViewById(R.id.app_label)
            val subtitle: TextView = view.findViewById(R.id.app_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget_picker_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.subtitle.text = item.appLabel
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
