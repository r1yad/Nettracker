package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.NetworkStatsHelper
import com.example.ui.theme.ColorDanger
import com.example.ui.theme.ColorSafe
import com.example.ui.theme.ColorWarning

@Composable
fun UsageGauge(
    usedBytes: Long,
    limitMb: Int,
    warningPercent: Int,
    modifier: Modifier = Modifier
) {
    val limitBytes = if (limitMb > 0) NetworkStatsHelper.mbToBytes(limitMb) else 1L
    val rawProgress = if (limitMb > 0) (usedBytes.toFloat() / limitBytes.toFloat()) else 0f
    val clampedProgress = rawProgress.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 900),
        label = "gauge_progress"
    )

    val statusColor = when {
        rawProgress >= 1f -> ColorDanger
        rawProgress >= (warningPercent / 100f) -> ColorWarning
        else -> ColorSafe
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(500),
        label = "gauge_color"
    )

    val statusLabel = when {
        rawProgress >= 1f -> "Limit Exceeded"
        rawProgress >= (warningPercent / 100f) -> "Warning (${(rawProgress * 100).toInt()}%)"
        else -> "Normal (${(rawProgress * 100).toInt()}%)"
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("usage_gauge"),
        contentAlignment = Alignment.Center
    ) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 18.dp.toPx()
            // Background arc (260 degrees arc for a dial look)
            val startAngle = 140f
            val sweepTotal = 260f

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Value arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = animatedColor,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = NetworkStatsHelper.formatBytes(usedBytes),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (limitMb > 0) "/ ${NetworkStatsHelper.formatBytes(limitBytes)} Limit" else "No Limit Set",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status Badge
            Box(
                modifier = Modifier
                    .background(
                        color = animatedColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = animatedColor
                )
            }
        }
    }
}
