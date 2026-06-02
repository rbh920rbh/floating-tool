package com.rbh920rbh.floatingtool

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class WidgetPickerActivity : AppCompatActivity() {

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finish()
            return
        }
        val widgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            ?: pendingWidgetId
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        if (manager.getAppWidgetInfo(widgetId) == null) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = AppWidgetManager.getInstance(this)
        pendingWidgetId = manager.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        try {
            pickWidget.launch(pickIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
            manager.deleteAppWidgetId(pendingWidgetId)
            finish()
        }
    }

    override fun onDestroy() {
        if (isFinishing && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val manager = AppWidgetManager.getInstance(this)
            if (OverlayItemStore.all().none { it is OverlayItem.WidgetSlot && it.appWidgetId == pendingWidgetId }) {
                try {
                    manager.deleteAppWidgetId(pendingWidgetId)
                } catch (_: Exception) {
                }
            }
        }
        super.onDestroy()
    }
}
