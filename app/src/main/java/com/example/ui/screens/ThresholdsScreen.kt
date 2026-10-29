package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UsageThresholds
import com.example.data.network.NetworkStatsHelper
import com.example.ui.theme.ColorWarning

@Composable
fun ThresholdsScreen(
    currentThresholds: UsageThresholds,
    onSaveThresholds: (UsageThresholds) -> Unit,
    onSendTestAlert: () -> Unit,
    onSendDailySummary: () -> Unit,
    onSendMonthlySummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dailyLimitMb by remember(currentThresholds) { mutableIntStateOf(currentThresholds.dailyLimitMb) }
    var dailyWarningPercent by remember(currentThresholds) { mutableIntStateOf(currentThresholds.dailyWarningPercent) }
    var dailyAlertEnabled by remember(currentThresholds) { mutableStateOf(currentThresholds.dailyAlertEnabled) }

    var monthlyLimitMb by remember(currentThresholds) { mutableIntStateOf(currentThresholds.monthlyLimitMb) }
    var monthlyWarningPercent by remember(currentThresholds) { mutableIntStateOf(currentThresholds.monthlyWarningPercent) }
    var monthlyAlertEnabled by remember(currentThresholds) { mutableStateOf(currentThresholds.monthlyAlertEnabled) }
    var billingCycleDay by remember(currentThresholds) { mutableIntStateOf(currentThresholds.billingCycleDay) }

    var backgroundServiceEnabled by remember(currentThresholds) { mutableStateOf(currentThresholds.backgroundServiceEnabled) }
    var dailyReportEnabled by remember(currentThresholds) { mutableStateOf(currentThresholds.dailyReportEnabled) }
    var monthlyReportEnabled by remember(currentThresholds) { mutableStateOf(currentThresholds.monthlyReportEnabled) }

    fun applyChanges() {
        onSaveThresholds(
            UsageThresholds(
                dailyLimitMb = dailyLimitMb,
                dailyWarningPercent = dailyWarningPercent,
                dailyAlertEnabled = dailyAlertEnabled,
                monthlyLimitMb = monthlyLimitMb,
                monthlyWarningPercent = monthlyWarningPercent,
                monthlyAlertEnabled = monthlyAlertEnabled,
                billingCycleDay = billingCycleDay,
                backgroundServiceEnabled = backgroundServiceEnabled,
                dailyReportEnabled = dailyReportEnabled,
                monthlyReportEnabled = monthlyReportEnabled
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("thresholds_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Daily Limit & Alerts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CrisisAlert,
                            contentDescription = "Daily Limit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Data Threshold",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Push notification when daily quota is surpassed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = dailyAlertEnabled,
                            onCheckedChange = {
                                dailyAlertEnabled = it
                                applyChanges()
                            },
                            modifier = Modifier.testTag("daily_alert_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Current Limit: ${dailyLimitMb} MB (${"%.2f".format(dailyLimitMb / 1024f)} GB)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Quick selector chips
                    val dailyPresets = listOf(500, 1024, 2048, 3072, 5120, 10240)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(dailyPresets) { preset ->
                            val label = if (preset >= 1024) "${preset / 1024} GB" else "${preset} MB"
                            FilterChip(
                                selected = dailyLimitMb == preset,
                                onClick = {
                                    dailyLimitMb = preset
                                    applyChanges()
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Slider(
                        value = dailyLimitMb.toFloat(),
                        onValueChange = {
                            dailyLimitMb = (it / 100).toInt() * 100
                        },
                        onValueChangeFinished = { applyChanges() },
                        valueRange = 100f..15000f,
                        modifier = Modifier.testTag("daily_limit_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Warning percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Warning Alert Trigger: $dailyWarningPercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "At ${(dailyLimitMb * (dailyWarningPercent / 100f)).toInt()} MB",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = ColorWarning
                        )
                    }

                    Slider(
                        value = dailyWarningPercent.toFloat(),
                        onValueChange = { dailyWarningPercent = it.toInt() },
                        onValueChangeFinished = { applyChanges() },
                        valueRange = 50f..95f,
                        steps = 8
                    )
                }
            }
        }

        // Section: Monthly Quota & Billing Cycle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Monthly Quota",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monthly Data Quota",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Alerts based on your telecom billing cycle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = monthlyAlertEnabled,
                            onCheckedChange = {
                                monthlyAlertEnabled = it
                                applyChanges()
                            },
                            modifier = Modifier.testTag("monthly_alert_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Monthly Limit: ${monthlyLimitMb / 1024} GB (${monthlyLimitMb} MB)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    val monthlyPresets = listOf(5120, 10240, 20480, 30720, 51200, 102400)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(monthlyPresets) { preset ->
                            FilterChip(
                                selected = monthlyLimitMb == preset,
                                onClick = {
                                    monthlyLimitMb = preset
                                    applyChanges()
                                },
                                label = { Text("${preset / 1024} GB") }
                            )
                        }
                    }

                    Slider(
                        value = monthlyLimitMb.toFloat(),
                        onValueChange = {
                            monthlyLimitMb = (it / 1024).toInt() * 1024
                        },
                        onValueChangeFinished = { applyChanges() },
                        valueRange = 1024f..153600f,
                        modifier = Modifier.testTag("monthly_limit_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Billing cycle day
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Billing Day",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Billing Cycle Reset Day: ${billingCycleDay}st/th",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Slider(
                        value = billingCycleDay.toFloat(),
                        onValueChange = { billingCycleDay = it.toInt() },
                        onValueChangeFinished = { applyChanges() },
                        valueRange = 1f..28f,
                        steps = 26
                    )
                }
            }
        }

        // Section: Background Service & Periodic Reports
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Background Execution & Reports",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure persistent monitoring and automated summary notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continuous Foreground Service",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Keeps a persistent notification with live data counts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = backgroundServiceEnabled,
                            onCheckedChange = {
                                backgroundServiceEnabled = it
                                applyChanges()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Usage Summary Notification",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Receive an evening summary of data used and top app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dailyReportEnabled,
                            onCheckedChange = {
                                dailyReportEnabled = it
                                applyChanges()
                            }
                        )
                    }
                }
            }
        }

        // Section: Instant Diagnostics & Notification Testing
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Test",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Preview & Testing",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Trigger immediate test notifications to verify alerting and summary styles on your device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSendTestAlert,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_test_alert_button")
                        ) {
                            Text("🚨 Trigger Threshold Test Alert")
                        }

                        OutlinedButton(
                            onClick = onSendDailySummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_daily_summary_button")
                        ) {
                            Text("📊 Trigger Daily Summary Report")
                        }

                        OutlinedButton(
                            onClick = onSendMonthlySummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_monthly_summary_button")
                        ) {
                            Text("📅 Trigger Monthly Summary Report")
                        }
                    }
                }
            }
        }
    }
}
