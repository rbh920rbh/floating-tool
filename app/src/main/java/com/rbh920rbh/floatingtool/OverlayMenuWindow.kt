package com.rbh920rbh.floatingtool

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayMenuWindow(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    fun show(anchorX: Int, anchorY: Int, onPick: (MenuAction) -> Unit) {
        dismiss()
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_FloatingTool)
        val menu = LayoutInflater.from(themed).inflate(R.layout.overlay_add_menu, null) as LinearLayout

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX
            y = anchorY + 8
        }

        bindMenuItem(menu, R.id.menu_add_app, MenuAction.AddApp, onPick)
        bindMenuItem(menu, R.id.menu_add_submenu, MenuAction.AddSubmenu, onPick)
        bindMenuItem(menu, R.id.menu_add_widget, MenuAction.AddWidget, onPick)
        bindMenuItem(menu, R.id.menu_close_panel, MenuAction.ClosePanel, onPick)

        windowManager.addView(menu, params)
        menuView = menu
        menuParams = params
    }

    private fun bindMenuItem(
        menu: LinearLayout,
        id: Int,
        action: MenuAction,
        onPick: (MenuAction) -> Unit,
    ) {
        menu.findViewById<TextView>(id).setOnClickListener {
            dismiss()
            onPick(action)
        }
    }

    fun showRemoveConfirm(anchorX: Int, anchorY: Int, onConfirm: () -> Unit) {
        dismiss()
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_FloatingTool)
        val menu = LayoutInflater.from(themed).inflate(R.layout.overlay_remove_menu, null) as LinearLayout
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX
            y = anchorY
        }
        menu.findViewById<TextView>(R.id.menu_remove_item).setOnClickListener {
            dismiss()
            onConfirm()
        }
        menu.findViewById<TextView>(R.id.menu_cancel).setOnClickListener { dismiss() }
        windowManager.addView(menu, params)
        menuView = menu
        menuParams = params
    }

    fun dismiss() {
        val view = menuView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        menuView = null
        menuParams = null
    }

    enum class MenuAction {
        AddApp,
        AddSubmenu,
        AddWidget,
        ClosePanel,
    }
}
