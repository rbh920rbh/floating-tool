package com.rbh920rbh.floatingtool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && Settings.canDrawOverlays(this)) {
            toggleOverlay()
        }
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handlePickerIntent(intent)

        requestNotificationPermissionIfNeeded()

        findViewById<MaterialButton>(R.id.btn_overlay_permission).setOnClickListener {
            openOverlaySettings()
        }

        findViewById<MaterialButton>(R.id.btn_toggle_overlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
                openOverlaySettings()
                return@setOnClickListener
            }
            toggleOverlay()
        }

        refreshUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePickerIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun handlePickerIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_WIDGET_PICKER -> {
                startActivity(Intent(this, WidgetPickerActivity::class.java))
            }
            ACTION_OPEN_SUBMENU_PICKER -> {
                startActivity(Intent(this, AppSubmenuPickerActivity::class.java))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        overlaySettingsLauncher.launch(intent)
    }

    private fun toggleOverlay() {
        if (FloatingOverlayService.isRunning) {
            stopService(Intent(this, FloatingOverlayService::class.java))
            Toast.makeText(this, R.string.overlay_stopped, Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            Toast.makeText(this, R.string.notification_permission_hint, Toast.LENGTH_LONG).show()
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, FloatingOverlayService::class.java),
        )
        Toast.makeText(this, R.string.overlay_started, Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    companion object {
        const val ACTION_OPEN_WIDGET_PICKER = "com.rbh920rbh.floatingtool.OPEN_WIDGET_PICKER"
        const val ACTION_OPEN_SUBMENU_PICKER = "com.rbh920rbh.floatingtool.OPEN_SUBMENU_PICKER"
    }

    private fun refreshUi() {
        val hasOverlay = Settings.canDrawOverlays(this)
        findViewById<SwitchMaterial>(R.id.switch_overlay_granted).isChecked = hasOverlay
        findViewById<MaterialButton>(R.id.btn_toggle_overlay).text = getString(
            if (FloatingOverlayService.isRunning) R.string.stop_floating_panel
            else R.string.start_floating_panel,
        )
    }
}
