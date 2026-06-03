package com.rbh920rbh.floatingtool

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import org.xmlpull.v1.XmlPullParser

/** 从目标应用 APK 的 shortcuts.xml 解析静态菜单项（补充 [LauncherApps] 读不到的项）。 */
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
        val result = linkedMapOf<String, ParsedShortcut>()
        val resources = try {
            packageManager.getResourcesForApplication(packageName)
        } catch (_: Exception) {
            return emptyList()
        }

        collectFromManifestMeta(packageManager, packageName, resources, result)
        return result.values.sortedBy { it.label.lowercase() }
    }

    private fun collectFromManifestMeta(
        packageManager: PackageManager,
        packageName: String,
        resources: Resources,
        result: MutableMap<String, ParsedShortcut>,
    ) {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags)
            }
        } catch (_: Exception) {
            return
        }

        pkgInfo.applicationInfo?.metaData?.getInt(META_SHORTCUTS, 0)?.takeIf { it != 0 }?.let { resId ->
            parseShortcutsXml(resources, resId, packageName).forEach { result.putIfAbsent(it.shortcutId, it) }
        }

        @Suppress("DEPRECATION")
        val activities = pkgInfo.activities ?: emptyArray()
        for (activity in activities) {
            val xmlResId = activity.metaData?.getInt(META_SHORTCUTS, 0) ?: 0
            if (xmlResId == 0) continue
            parseShortcutsXml(resources, xmlResId, packageName).forEach { result.putIfAbsent(it.shortcutId, it) }
        }
    }

    private fun parseShortcutsXml(
        resources: Resources,
        xmlResId: Int,
        packageName: String,
    ): List<ParsedShortcut> {
        val shortcuts = mutableListOf<ParsedShortcut>()
        val parser = resources.getXml(xmlResId)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == TAG_SHORTCUT) {
                val enabled = parser.getAttributeValue(ANDROID_NS, ATTR_ENABLED)
                if (enabled == "false") {
                    event = parser.next()
                    continue
                }
                val id = parser.getAttributeValue(ANDROID_NS, ATTR_SHORTCUT_ID) ?: ""
                if (id.isNotBlank()) {
                    val label = readShortcutLabel(parser, resources) ?: id
                    val intentUri = readShortcutIntent(parser, packageName)
                    shortcuts.add(ParsedShortcut(id, label, intentUri))
                }
            }
            event = parser.next()
        }
        return shortcuts
    }

    private fun readShortcutLabel(parser: XmlPullParser, resources: Resources): String? {
        val shortText = parser.getAttributeValue(ANDROID_NS, ATTR_SHORT_LABEL)
        if (!shortText.isNullOrBlank() && !shortText.startsWith("@")) return shortText
        val longText = parser.getAttributeValue(ANDROID_NS, ATTR_LONG_LABEL)
        if (!longText.isNullOrBlank() && !longText.startsWith("@")) return longText
        val xmlParser = parser as? XmlResourceParser ?: return null
        val shortRes = xmlParser.getAttributeResourceValue(ANDROID_NS, ATTR_SHORT_LABEL, 0)
        if (shortRes != 0) {
            try {
                return resources.getString(shortRes)
            } catch (_: Exception) {
            }
        }
        val longRes = xmlParser.getAttributeResourceValue(ANDROID_NS, ATTR_LONG_LABEL, 0)
        if (longRes != 0) {
            try {
                return resources.getString(longRes)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun readShortcutIntent(parser: XmlPullParser, packageName: String): String? {
        val startDepth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && parser.depth > startDepth) {
            if (event == XmlPullParser.START_TAG && parser.name == TAG_INTENT) {
                val intent = Intent()
                var inner = parser.next()
                while (inner != XmlPullParser.END_DOCUMENT && parser.depth > startDepth + 1) {
                    if (inner == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            TAG_ACTION -> intent.action = parser.nextText()
                            TAG_CATEGORY -> intent.addCategory(parser.nextText())
                            TAG_COMPONENT -> {
                                val cls = parser.getAttributeValue(ANDROID_NS, ATTR_NAME)
                                if (!cls.isNullOrBlank()) {
                                    intent.setClassName(packageName, cls)
                                }
                            }
                            TAG_DATA -> {
                                val scheme = parser.getAttributeValue(ANDROID_NS, ATTR_SCHEME)
                                val host = parser.getAttributeValue(ANDROID_NS, "host")
                                val path = parser.getAttributeValue(ANDROID_NS, "path")
                                if (!scheme.isNullOrBlank()) {
                                    val uri = buildString {
                                        append(scheme)
                                        append("://")
                                        if (!host.isNullOrBlank()) append(host)
                                        if (!path.isNullOrBlank()) append(path)
                                    }
                                    intent.data = android.net.Uri.parse(uri)
                                }
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
                if (intent.component == null && intent.`package` == null) {
                    intent.setPackage(packageName)
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
    private const val TAG_COMPONENT = "component"
    private const val TAG_DATA = "data"
    private const val TAG_EXTRA = "extra"
    private const val ATTR_SHORTCUT_ID = "shortcutId"
    private const val ATTR_SHORT_LABEL = "shortcutShortLabel"
    private const val ATTR_LONG_LABEL = "shortcutLongLabel"
    private const val ATTR_ENABLED = "enabled"
    private const val ATTR_SCHEME = "scheme"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"
}
