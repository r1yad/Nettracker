package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.data.model.TimeSpan
import com.example.data.network.NetworkStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetTrackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NetTrackWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val prefsManager = PreferencesManager(context)
                val thresholds = prefsManager.getThresholds()

                val (todayStart, todayEnd) = NetworkStatsHelper.getTimeRange(TimeSpan.TODAY)
                val todayUsage = NetworkStatsHelper.getDeviceUsage(context, todayStart, todayEnd)

                val dailyLimitBytes = NetworkStatsHelper.mbToBytes(thresholds.dailyLimitMb)
                val cellularBytes = todayUsage.mobileBytes
                val wifiBytes = todayUsage.wifiBytes

                val ratio = if (dailyLimitBytes > 0) {
                    (cellularBytes.toFloat() / dailyLimitBytes.toFloat()).coerceIn(0f, 1.5f)
                } else 0f

                val percent = (ratio * 100).toInt()

                // Render circular gauge bitmap
                val gaugeBitmap = renderCellularGaugeBitmap(
                    ratio = ratio.coerceAtMost(1f),
                    percentage = percent,
                    warningPercent = thresholds.dailyWarningPercent
                )

                val views = RemoteViews(context.packageName, R.layout.widget_nettrack)

                // 1. Cellular Side
                views.setImageViewBitmap(R.id.widget_cellular_gauge, gaugeBitmap)
                val cellStr = NetworkStatsHelper.formatBytes(cellularBytes)
                val limitStr = if (thresholds.dailyLimitMb >= 1024) {
                    "${"%.1f".format(thresholds.dailyLimitMb / 1024f)} GB"
                } else {
                    "${thresholds.dailyLimitMb} MB"
                }
                views.setTextViewText(R.id.widget_cellular_text, "$cellStr / $limitStr")

                // 2. Wi-Fi Side
                views.setTextViewText(R.id.widget_wifi_value, NetworkStatsHelper.formatBytes(wifiBytes))
                views.setTextViewText(R.id.widget_wifi_subtext, "Today's Wi-Fi")

                // 3. Click action: Open MainActivity
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun renderCellularGaugeBitmap(
            ratio: Float,
            percentage: Int,
            warningPercent: Int
        ): Bitmap {
            val size = 180
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val strokeWidth = 16f
            val padding = 14f
            val rect = RectF(padding, padding, size - padding, size - padding)

            // Background arc paint
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
            }

            val startAngle = 140f
            val sweepTotal = 260f

            // Draw track
            canvas.drawArc(rect, startAngle, sweepTotal, false, trackPaint)

            // Value arc paint
            val valueColor = when {
                percentage >= 100 -> Color.parseColor("#EF4444") // Danger
                percentage >= warningPercent -> Color.parseColor("#F59E0B") // Warning
                else -> Color.parseColor("#06B6D4") // Cyan
            }

            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valueColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
            }

            if (ratio > 0f) {
                canvas.drawArc(rect, startAngle, sweepTotal * ratio, false, valuePaint)
            }

            // Percentage text inside center
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("$percentage%", size / 2f, textY, textPaint)

            return bitmap
        }
    }
}
