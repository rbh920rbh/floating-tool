package com.rbh920rbh.floatingtool

import org.json.JSONArray
import org.json.JSONObject

sealed class OverlayItem {
    abstract val id: String

    data class AppShortcut(
        override val id: String,
        val packageName: String,
        val label: String,
    ) : OverlayItem()

    /** 应用桌面长按菜单中的快捷方式（二级菜单项） */
    data class AppSubmenuShortcut(
        override val id: String,
        val packageName: String,
        val shortcutId: String,
        val label: String,
    ) : OverlayItem()

    data class WidgetSlot(
        override val id: String,
        val appWidgetId: Int,
    ) : OverlayItem()
}

object OverlayItemStore {
    private const val PREFS = "overlay_items"
    private const val KEY_ITEMS = "items"

    private val items = mutableListOf<OverlayItem>()

    fun load(context: android.content.Context) {
        items.clear()
        val raw = context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ITEMS, null)
            ?: return
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            when (obj.getString("type")) {
                "app" -> items.add(
                    OverlayItem.AppShortcut(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        label = obj.getString("label"),
                    ),
                )
                "submenu" -> items.add(
                    OverlayItem.AppSubmenuShortcut(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        shortcutId = obj.getString("shortcutId"),
                        label = obj.getString("label"),
                    ),
                )
                "widget" -> items.add(
                    OverlayItem.WidgetSlot(
                        id = obj.getString("id"),
                        appWidgetId = obj.getInt("appWidgetId"),
                    ),
                )
            }
        }
    }

    fun save(context: android.content.Context) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            when (item) {
                is OverlayItem.AppShortcut -> {
                    obj.put("type", "app")
                    obj.put("id", item.id)
                    obj.put("packageName", item.packageName)
                    obj.put("label", item.label)
                }
                is OverlayItem.AppSubmenuShortcut -> {
                    obj.put("type", "submenu")
                    obj.put("id", item.id)
                    obj.put("packageName", item.packageName)
                    obj.put("shortcutId", item.shortcutId)
                    obj.put("label", item.label)
                }
                is OverlayItem.WidgetSlot -> {
                    obj.put("type", "widget")
                    obj.put("id", item.id)
                    obj.put("appWidgetId", item.appWidgetId)
                }
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    fun all(): List<OverlayItem> = items.toList()

    fun add(context: android.content.Context, item: OverlayItem) {
        items.add(item)
        save(context)
    }

    fun remove(context: android.content.Context, id: String) {
        val removed = items.removeAll { it.id == id }
        if (removed) save(context)
    }

    private const val MODE_PRIVATE = android.content.Context.MODE_PRIVATE
}
