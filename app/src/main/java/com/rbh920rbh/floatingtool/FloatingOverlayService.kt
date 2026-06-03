package com.rbh920rbh.floatingtool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class FloatingOverlayService : Service(), OverlayPanelView.Callbacks {

    private lateinit var windowManager: WindowManager
    private var overlayPanel: OverlayPanelView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var menuWindow: OverlayMenuWindow? = null

    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector

    private val itemsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshPanelContents()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        menuWindow = OverlayMenuWindow(this, windowManager)

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingTool)
        gestureDetector = GestureDetector(
            themedContext,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onLongPress(e: MotionEvent) {
                    if (!isDragging) {
                        val params = layoutParams ?: return
                        showAddMenu(params.x, params.y + (overlayPanel?.height ?: 0) / 2)
                    }
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    val params = layoutParams ?: return false
                    val panel = overlayPanel ?: return false
                    if (e1 == null) return false
                    if (!isDragging) {
                        val dx = e2.rawX - e1.rawX
                        val dy = e2.rawY - e1.rawY
                        if (dx * dx + dy * dy < DRAG_SLOP_PX * DRAG_SLOP_PX) return false
                        isDragging = true
                        initialX = params.x
                        initialY = params.y
                        touchStartX = e1.rawX
                        touchStartY = e1.rawY
                    }
                    params.x = initialX + (e2.rawX - touchStartX).toInt()
                    params.y = initialY + (e2.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(panel, params)
                    return true
                }
            },
        )

        ContextCompat.registerReceiver(
            this,
            itemsChangedReceiver,
            IntentFilter(AppPickerActivity.ACTION_OVERLAY_ITEMS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(applicationContext, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
            stopSelfSafely()
            return
        }

        try {
            promoteToForeground()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            Toast.makeText(applicationContext, R.string.error_foreground_service, Toast.LENGTH_LONG).show()
            stopSelfSafely()
            return
        }

        try {
            OverlayItemStore.load(this)
            showOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed", e)
            Toast.makeText(applicationContext, R.string.error_overlay_failed, Toast.LENGTH_LONG).show()
            stopSelfSafely()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(itemsChangedReceiver)
        } catch (_: Exception) {
        }
        overlayPanel?.stopWidgetHost()
        menuWindow?.dismiss()
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRequestAddMenu(anchorX: Int, anchorY: Int) {
        showAddMenu(anchorX, anchorY)
    }

    override fun onRequestRemoveItem(item: OverlayItem) {
        menuWindow?.showRemoveConfirm(anchorX = layoutParams?.x ?: 0, anchorY = layoutParams?.y ?: 0) {
            if (item is OverlayItem.WidgetSlot) {
                try {
                    AppWidgetHost(this, OverlayPanelView.APP_WIDGET_HOST_ID)
                        .deleteAppWidgetId(item.appWidgetId)
                } catch (_: Exception) {
                }
            }
            OverlayItemStore.remove(this, item.id)
            refreshPanelContents()
            Toast.makeText(this, R.string.item_removed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPanelSizeChanged() {
        mainHandler.post { updateOverlaySize() }
    }

    private fun showOverlay() {
        if (overlayPanel != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingTool)
        val panel = OverlayPanelView(themedContext, this)
        panel.startWidgetHost()

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

        panel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
            }
            gestureDetector.onTouchEvent(event)
            true
        }

        windowManager.addView(panel, params)
        overlayPanel = panel
        layoutParams = params
        refreshPanelContents()
    }

    private fun refreshPanelContents() {
        OverlayItemStore.load(this)
        overlayPanel?.bindItems(OverlayItemStore.all())
    }

    private fun updateOverlaySize() {
        val panel = overlayPanel ?: return
        val params = layoutParams ?: return
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(panel, params)
    }

    private fun startPickerActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass).apply {
            // 勿使用 CLEAR_TOP，否则会闪回 MainActivity
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startPickerActivity failed", e)
            Toast.makeText(applicationContext, R.string.error_launch_intent, Toast.LENGTH_LONG).show()
        }
    }

    /** 经 MainActivity 中转，避免后台直接拉起选择页被系统拦截 */
    private fun startPickerViaMain(action: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startPickerViaMain failed", e)
            Toast.makeText(applicationContext, R.string.error_launch_intent, Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddMenu(anchorX: Int, anchorY: Int) {
        menuWindow?.show(anchorX, anchorY) { action ->
            when (action) {
                OverlayMenuWindow.MenuAction.AddApp -> {
                    startPickerActivity(AppPickerActivity::class.java)
                }
                OverlayMenuWindow.MenuAction.AddSubmenu -> {
                    startPickerActivity(AppSubmenuPickerActivity::class.java)
                }
                OverlayMenuWindow.MenuAction.AddWidget -> {
                    startPickerViaMain(MainActivity.ACTION_OPEN_WIDGET_PICKER)
                }
                OverlayMenuWindow.MenuAction.ClosePanel -> stopSelf()
            }
        }
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        isRunning = false
        stopSelf()
    }

    private fun removeOverlay() {
        overlayPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayPanel = null
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "FloatingOverlayService"
        private const val CHANNEL_ID = "floating_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val DRAG_SLOP_PX = 12f

        @Volatile
        var isRunning: Boolean = false
    }
}
