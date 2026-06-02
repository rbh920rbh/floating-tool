package com.rbh920rbh.floatingtool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.ContextThemeWrapper
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.app.Service

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onDestroy() {
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlayView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val panel = inflater.inflate(R.layout.overlay_floating_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 200
        }

        panel.setOnTouchListener { _, event -> handleDrag(event, params, panel) }
        panel.setOnLongClickListener {
            showAddMenu()
            true
        }

        windowManager.addView(panel, params)
        overlayView = panel
        layoutParams = params
    }

    private fun handleDrag(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        panel: View,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                touchStartX = event.rawX
                touchStartY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - touchStartX).toInt()
                params.y = initialY + (event.rawY - touchStartY).toInt()
                windowManager.updateViewLayout(panel, params)
                return true
            }
        }
        return false
    }

    private fun showAddMenu() {
        val anchor = overlayView ?: return
        val options = listOf(
            getString(R.string.menu_pin_shortcut),
            getString(R.string.menu_pick_widget),
            getString(R.string.menu_close_panel),
        )
        val popup = ListPopupWindow(ContextThemeWrapper(this, R.style.Theme_FloatingTool)).apply {
            setAdapter(ArrayAdapter(this@FloatingOverlayService, android.R.layout.simple_list_item_1, options))
            anchorView = anchor
            isModal = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            setOnItemClickListener { _, _, position, _ ->
                dismiss()
                when (position) {
                    0 -> launchPinShortcutPicker()
                    1 -> launchWidgetPicker()
                    2 -> stopSelf()
                }
            }
        }
        popup.show()
    }

    private fun launchPinShortcutPicker() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pickIntent = Intent.createChooser(launcherIntent, getString(R.string.menu_pin_shortcut))
        pickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(pickIntent)
            Toast.makeText(this, R.string.hint_pin_shortcut, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_launch_intent, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchWidgetPicker() {
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(pickIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_widget_picker, Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        layoutParams = null
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "floating_overlay"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
    }
}
