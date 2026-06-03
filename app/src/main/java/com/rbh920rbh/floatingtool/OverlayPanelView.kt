package com.rbh920rbh.floatingtool

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.UUID

class OverlayPanelView(
    context: Context,
    private val callbacks: Callbacks,
) : LinearLayout(context) {

    interface Callbacks {
        fun onRequestAddMenu(anchorX: Int, anchorY: Int)
        fun onRequestRemoveItem(item: OverlayItem)
        fun onPanelSizeChanged()
    }

    private val emptyHint: TextView
    private val itemsGrid: GridLayout
    private val appWidgetHost = AppWidgetHost(context, APP_WIDGET_HOST_ID)
    private val hostViews = mutableMapOf<Int, AppWidgetHostView>()

    init {
        orientation = VERTICAL
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        background = ContextCompat.getDrawable(context, R.drawable.overlay_panel_background)

        emptyHint = TextView(context).apply {
            text = context.getString(R.string.overlay_panel_hint)
            setTextColor(ContextCompat.getColor(context, R.color.overlay_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        addView(
            emptyHint,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )

        itemsGrid = GridLayout(context).apply {
            columnCount = 3
            useDefaultMargins = false
        }
        addView(
            itemsGrid,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )

        minimumWidth = dp(96)
        minimumHeight = dp(96)
    }

    fun startWidgetHost() {
        appWidgetHost.startListening()
    }

    fun stopWidgetHost() {
        appWidgetHost.stopListening()
    }

    fun bindItems(items: List<OverlayItem>) {
        itemsGrid.removeAllViews()
        hostViews.clear()

        if (items.isEmpty()) {
            emptyHint.visibility = VISIBLE
            itemsGrid.visibility = GONE
            itemsGrid.columnCount = 1
        } else {
            emptyHint.visibility = GONE
            itemsGrid.visibility = VISIBLE
            val columns = gridColumns(items.size)
            itemsGrid.columnCount = columns
            items.forEachIndexed { index, item ->
                val view = createItemView(item)
                val row = index / columns
                val col = index % columns
                val lp = GridLayout.LayoutParams().apply {
                    width = GridLayout.LayoutParams.WRAP_CONTENT
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(col)
                    rowSpec = GridLayout.spec(row)
                }
                view.layoutParams = lp
                itemsGrid.addView(view)
            }
        }

        requestLayout()
        callbacks.onPanelSizeChanged()
    }

    private fun createItemView(item: OverlayItem): View {
        return when (item) {
            is OverlayItem.AppShortcut -> createAppView(item)
            is OverlayItem.AppSubmenuShortcut -> createSubmenuShortcutView(item)
            is OverlayItem.WidgetSlot -> createWidgetView(item)
        }
    }

    private fun createAppView(item: OverlayItem.AppShortcut): View {
        val cell = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val cellPad = dp(4)
            setPadding(cellPad, cellPad, cellPad, cellPad)
        }
        val icon = ImageView(context).apply {
            val size = dp(48)
            layoutParams = LayoutParams(size, size)
            try {
                setImageDrawable(context.packageManager.getApplicationIcon(item.packageName))
            } catch (_: Exception) {
                setImageResource(R.drawable.ic_launcher_foreground)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val label = TextView(context).apply {
            text = item.label
            setTextColor(ContextCompat.getColor(context, R.color.overlay_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = 1
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
        }
        cell.addView(icon)
        cell.addView(
            label,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            },
        )

        cell.minimumWidth = dp(72)

        cell.setOnClickListener {
            launchApp(item.packageName)
        }
        cell.setOnLongClickListener {
            callbacks.onRequestRemoveItem(item)
            true
        }
        return cell
    }

    private fun createSubmenuShortcutView(item: OverlayItem.AppSubmenuShortcut): View {
        val cell = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val icon = ImageView(context).apply {
            layoutParams = LayoutParams(dp(48), dp(48))
            try {
                setImageDrawable(context.packageManager.getApplicationIcon(item.packageName))
            } catch (_: Exception) {
                setImageResource(R.drawable.ic_launcher_foreground)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val label = TextView(context).apply {
            text = item.label
            setTextColor(ContextCompat.getColor(context, R.color.overlay_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = 2
            gravity = Gravity.CENTER
        }
        cell.addView(icon)
        cell.addView(
            label,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            },
        )
        cell.minimumWidth = dp(72)
        cell.setOnClickListener { launchSubmenuShortcut(item) }
        cell.setOnLongClickListener {
            callbacks.onRequestRemoveItem(item)
            true
        }
        return cell
    }

    private fun launchSubmenuShortcut(item: OverlayItem.AppSubmenuShortcut) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            val opts = android.app.ActivityOptions.makeBasic().toBundle()
            try {
                launcherApps.startShortcut(
                    item.packageName,
                    item.shortcutId,
                    null,
                    opts,
                    Process.myUserHandle(),
                )
                return
            } catch (_: Exception) {
            }
        }
        val uri = item.launchIntentUri
        if (!uri.isNullOrBlank()) {
            try {
                val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        launchApp(item.packageName)
    }

    private fun createWidgetView(item: OverlayItem.WidgetSlot): View {
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.getAppWidgetInfo(item.appWidgetId)
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            val cellPad = dp(4)
            setPadding(cellPad, cellPad, cellPad, cellPad)
        }

        if (provider != null) {
            val hostView = appWidgetHost.createView(context, item.appWidgetId, provider)
            hostViews[item.appWidgetId] = hostView
            wrapper.addView(
                hostView,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
            )
        } else {
            wrapper.addView(
                TextView(context).apply {
                    text = context.getString(R.string.widget_unavailable)
                    setTextColor(ContextCompat.getColor(context, R.color.overlay_text))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                },
            )
        }

        wrapper.minimumWidth = dp(100)

        wrapper.setOnLongClickListener {
            callbacks.onRequestRemoveItem(item)
            true
        }
        return wrapper
    }

    private fun launchApp(packageName: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }

    private fun gridColumns(count: Int): Int = when {
        count <= 1 -> 1
        count <= 4 -> 2
        else -> 3
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        const val APP_WIDGET_HOST_ID = 0xF100
    }
}

fun newOverlayItemId(): String = UUID.randomUUID().toString()
