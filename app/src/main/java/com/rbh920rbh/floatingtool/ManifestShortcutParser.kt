package com.rbh920rbh.floatingtool

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import org.xmlpull.v1.XmlPullParser

/**
 * 非桌面应用无法使用 LauncherApps.getShortcuts 时的回退方案：
 * 从目标应用 APK 的 shortcuts.xml（android.app.shortcuts）解析静态菜单项。
 */
object ManifestShortcutParser {

    data class ParsedShortcut(
        val shortcutId: String,
        val label: String,
        val launchIntentUri: String?,
    )

    fun loadStaticShortcuts(
        packageManager: PackageManager,
        packageName: String,
    ): List<ParsedShortcut> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val flags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_META_DATA
        @Suppress("DEPRECATION")
        val activities = packageManager.queryIntentActivities(launcherIntent, flags)
        if (activities.isEmpty()) return emptyList()

        val resources = try {
            packageManager.getResourcesForApplication(packageName)
        } catch (_: Exception) {
            return emptyList()
        }

        val result = linkedMapOf<String, ParsedShortcut>()
        for (resolve in activities) {
            val meta = resolve.activityInfo.metaData ?: continue
            val xmlResId = meta.getInt(META_SHORTCUTS, 0)
            if (xmlResId == 0) continue
            parseShortcutsXml(resources, xmlResId).forEach { shortcut ->
                result.putIfAbsent(shortcut.shortcutId, shortcut)
            }
        }
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val appXml = appInfo.metaData?.getInt(META_SHORTCUTS, 0) ?: 0
            if (appXml != 0) {
                parseShortcutsXml(resources, appXml).forEach { shortcut ->
                    result.putIfAbsent(shortcut.shortcutId, shortcut)
                }
            }
        } catch (_: Exception) {
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    private fun parseShortcutsXml(resources: Resources, xmlResId: Int): List<ParsedShortcut> {
        val shortcuts = mutableListOf<ParsedShortcut>()
        val parser = resources.getXml(xmlResId)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == TAG_SHORTCUT) {
                val id = parser.getAttributeValue(ANDROID_NS, ATTR_SHORTCUT_ID) ?: ""
                if (id.isNotBlank()) {
                    val shortLabelRes = parser.getAttributeResourceValue(
                        ANDROID_NS,
                        ATTR_SHORT_LABEL,
                        0,
                    )
                    val longLabelRes = parser.getAttributeResourceValue(
                        ANDROID_NS,
                        ATTR_LONG_LABEL,
                        0,
                    )
                    val label = when {
                        shortLabelRes != 0 -> resources.getString(shortLabelRes)
                        longLabelRes != 0 -> resources.getString(longLabelRes)
                        else -> id
                    }
                    val intentUri = readShortcutIntent(parser)
                    shortcuts.add(ParsedShortcut(id, label, intentUri))
                }
            }
            event = parser.next()
        }
        return shortcuts
    }

    private fun readShortcutIntent(parser: XmlPullParser): String? {
        var depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && parser.depth > depth) {
            if (event == XmlPullParser.START_TAG && parser.name == TAG_INTENT) {
                val intent = Intent()
                var inner = parser.next()
                while (inner != XmlPullParser.END_DOCUMENT && parser.depth > depth + 1) {
                    if (inner == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            TAG_ACTION -> intent.action = parser.nextText()
                            TAG_CATEGORY -> intent.addCategory(parser.nextText())
                            TAG_DATA -> {
                                val scheme = parser.getAttributeValue(ANDROID_NS, ATTR_SCHEME)
                                if (!scheme.isNullOrBlank()) intent.data =
                                    android.net.Uri.parse("$scheme:")
                            }
                            TAG_EXTRA -> {
                                val name = parser.getAttributeValue(ANDROID_NS, ATTR_NAME)
                                val value = parser.getAttributeValue(ANDROID_NS, ATTR_VALUE)
                                if (!name.isNullOrBlank() && value != null) {
                                    intent.putExtra(name, value)
                                }
                            }
                        }
                    }
                    inner = parser.next()
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return intent.toUri(Intent.URI_INTENT_SCHEME)
            }
            event = parser.next()
        }
        return null
    }

    private const val META_SHORTCUTS = "android.app.shortcuts"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TAG_SHORTCUT = "shortcut"
    private const val TAG_INTENT = "intent"
    private const val TAG_ACTION = "action"
    private const val TAG_CATEGORY = "category"
    private const val TAG_DATA = "data"
    private const val TAG_EXTRA = "extra"
    private const val ATTR_SHORTCUT_ID = "shortcutId"
    private const val ATTR_SHORT_LABEL = "shortcutShortLabel"
    private const val ATTR_LONG_LABEL = "shortcutLongLabel"
    private const val ATTR_SCHEME = "scheme"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"
}
