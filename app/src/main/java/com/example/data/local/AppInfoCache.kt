package com.example.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

object AppInfoCache {
    // Cache up to 150 bitmaps in memory (approx 1-2 MB max, ultra lightweight)
    private val iconCache = LruCache<String, Bitmap>(150)
    // Cache UID -> (AppName, PackageName, IsSystem)
    private val uidCache = LruCache<Int, Triple<String, String, Boolean>>(200)

    fun getCachedInfo(uid: Int): Triple<String, String, Boolean>? {
        return uidCache.get(uid)
    }

    fun putCachedInfo(uid: Int, info: Triple<String, String, Boolean>) {
        uidCache.put(uid, info)
    }

    fun getCachedIcon(packageName: String): Bitmap? {
        return iconCache.get(packageName)
    }

    fun getOrLoadIcon(context: Context, packageName: String): Bitmap? {
        val cached = iconCache.get(packageName)
        if (cached != null) return cached

        return try {
            val pm = context.packageManager
            val drawable: Drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            iconCache.put(packageName, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun preloadCommonAppInfo(context: Context) {
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val uid = appInfo.uid
                if (uidCache.get(uid) == null) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    uidCache.put(uid, Triple(label, appInfo.packageName, isSystem))
                }
            }
        } catch (_: Exception) {}
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(128) else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(128) else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
