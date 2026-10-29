package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage_records")
data class DailyUsageRecord(
    @PrimaryKey
    val date: String, // YYYY-MM-DD
    val mobileBytes: Long,
    val wifiBytes: Long,
    val totalBytes: Long,
    val topAppName: String? = null,
    val topAppBytes: Long? = null
)

@Entity(tableName = "alert_logs")
data class AlertLogRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val title: String,
    val message: String,
    val usedBytes: Long,
    val thresholdBytes: Long
)
