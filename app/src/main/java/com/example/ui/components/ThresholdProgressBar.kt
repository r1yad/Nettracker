package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.network.NetworkStatsHelper
import com.example.ui.theme.ColorDanger
import com.example.ui.theme.ColorSafe
import com.example.ui.theme.ColorWarning

@Composable
fun ThresholdProgressBar(
    title: String,
    usedBytes: Long,
    limitMb: Int,
    warningPercent: Int,
    modifier: Modifier = Modifier
) {
    val limitBytes = if (limitMb > 0) NetworkStatsHelper.mbToBytes(limitMb) else 1L
    val rawRatio = if (limitMb > 0) (usedBytes.toFloat() / limitBytes.toFloat()) else 0f
    val clampedRatio = rawRatio.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = clampedRatio,
        animationSpec = tween(700),
        label = "progress_ratio"
    )

    val barColor = when {
        rawRatio >= 1f -> ColorDanger
        rawRatio >= (warningPercent / 100f) -> ColorWarning
        else -> ColorSafe
    }

    val percentageInt = (rawRatio * 100).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "$percentageInt%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = barColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${NetworkStatsHelper.formatBytes(usedBytes)} used",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (limitMb > 0) "Limit: ${NetworkStatsHelper.formatBytes(limitBytes)}" else "No limit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
