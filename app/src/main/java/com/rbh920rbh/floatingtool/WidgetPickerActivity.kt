package com.rbh920rbh.floatingtool

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class WidgetPickerActivity : AppCompatActivity() {

    private lateinit var widgetHost: AppWidgetHost
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when {
            result.resultCode != Activity.RESULT_OK -> finish()
            else -> handleWidgetPick(
                result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                    ?: pendingWidgetId,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = AppWidgetHost(this, OverlayPanelView.APP_WIDGET_HOST_ID)
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        try {
            pickWidget.launch(pickIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
            releasePendingWidgetId()
            finish()
        }
    }

    private fun handleWidgetPick(widgetId: Int) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            releasePendingWidgetId()
            finish()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        if (manager.getAppWidgetInfo(widgetId) == null) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
            releasePendingWidgetId()
            finish()
            return
        }
        OverlayItemStore.add(
            this,
            OverlayItem.WidgetSlot(id = newOverlayItemId(), appWidgetId = widgetId),
        )
        sendBroadcast(
            Intent(AppPickerActivity.ACTION_OVERLAY_ITEMS_CHANGED).setPackage(packageName),
        )
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) {
            releasePendingWidgetIdIfUnused()
        }
        super.onDestroy()
    }

    private fun releasePendingWidgetIdIfUnused() {
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val stillPending = OverlayItemStore.all()
            .none { it is OverlayItem.WidgetSlot && it.appWidgetId == pendingWidgetId }
        if (stillPending) {
            releasePendingWidgetId()
        }
    }

    private fun releasePendingWidgetId() {
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        try {
            if (::widgetHost.isInitialized) {
                widgetHost.deleteAppWidgetId(pendingWidgetId)
            }
        } catch (_: Exception) {
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }
}
