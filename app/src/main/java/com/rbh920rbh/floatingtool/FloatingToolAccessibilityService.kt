package com.rbh920rbh.floatingtool

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 用于测试：部分机型读取应用快捷方式可能依赖已开启的无障碍服务。
 * 当前不监听或操作界面，仅作为可开关的辅助功能项。
 */
class FloatingToolAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
