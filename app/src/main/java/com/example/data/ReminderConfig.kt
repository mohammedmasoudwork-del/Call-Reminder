package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_config")
data class ReminderConfig(
    @PrimaryKey val id: Int = 1,
    val contactName: String = "أختي",
    val phoneNumber: String = "",
    val intervalDays: Int = 1, // 1: every day, 2: every 2 days, 7: every week
    val snoozeMinutes: Int = 30, // 30: 30 minutes, 60: 1 hour
    val minCallDurationSeconds: Int = 30, // Default 30 seconds
    val isReminderEnabled: Boolean = false,
    val lastCallTimestamp: Long = 0L,
    val nextReminderTimestamp: Long = 0L // Saved timestamp for displaying or recovering alarm
)
