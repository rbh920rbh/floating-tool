package com.rbh920rbh.floatingtool

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class WidgetPickerActivity : AppCompatActivity() {

    private lateinit var widgetHost: AppWidgetHost
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pickerLaunched = false

    private val pickWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when {
            result.resultCode != Activity.RESULT_OK -> {
                toastAndFinish(R.string.widget_pick_cancelled)
            }
            else -> {
                val widgetId = result.data?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    pendingWidgetId,
                ) ?: pendingWidgetId
                bindOrSaveWidget(widgetId)
            }
        }
    }

    private val bindWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> commitWidget(pendingWidgetId)
            else -> {
                releasePendingWidgetId()
                toastAndFinish(R.string.widget_bind_denied)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_picker)

        widgetHost = AppWidgetHost(this, OverlayPanelView.APP_WIDGET_HOST_ID)
        if (savedInstanceState != null) {
            pickerLaunched = savedInstanceState.getBoolean(STATE_PICKER_LAUNCHED, false)
            pendingWidgetId = savedInstanceState.getInt(
                STATE_PENDING_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        }
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingWidgetId = widgetHost.allocateAppWidgetId()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!pickerLaunched) {
            pickerLaunched = true
            launchWidgetPicker()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PICKER_LAUNCHED, pickerLaunched)
        outState.putInt(STATE_PENDING_WIDGET_ID, pendingWidgetId)
    }

    private fun launchWidgetPicker() {
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        if (pickIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_LONG).show()
            releasePendingWidgetId()
            finish()
            return
        }
        try {
            pickWidget.launch(pickIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_LONG).show()
            releasePendingWidgetId()
            finish()
        }
    }

    private fun bindOrSaveWidget(widgetId: Int) {
        pendingWidgetId = widgetId
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            releasePendingWidgetId()
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(widgetId)?.provider
        if (provider == null) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
            releasePendingWidgetId()
            finish()
            return
        }

        if (manager.bindAppWidgetIdIfAllowed(widgetId, provider)) {
            commitWidget(widgetId)
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        if (bindIntent.resolveActivity(packageManager) != null) {
            try {
                bindWidget.launch(bindIntent)
            } catch (_: Exception) {
                commitWidget(widgetId)
            }
        } else {
            commitWidget(widgetId)
        }
    }

    private fun commitWidget(widgetId: Int) {
        OverlayItemStore.load(this)
        OverlayItemStore.add(
            this,
            OverlayItem.WidgetSlot(id = newOverlayItemId(), appWidgetId = widgetId),
        )
        sendBroadcast(
            Intent(AppPickerActivity.ACTION_OVERLAY_ITEMS_CHANGED).setPackage(packageName),
        )
        Toast.makeText(this, R.string.widget_added, Toast.LENGTH_SHORT).show()
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
        OverlayItemStore.load(this)
        val inUse = OverlayItemStore.all()
            .any { it is OverlayItem.WidgetSlot && it.appWidgetId == pendingWidgetId }
        if (!inUse) {
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

    private fun toastAndFinish(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        releasePendingWidgetId()
        finish()
    }

    companion object {
        private const val STATE_PICKER_LAUNCHED = "picker_launched"
        private const val STATE_PENDING_WIDGET_ID = "pending_widget_id"
    }
}
