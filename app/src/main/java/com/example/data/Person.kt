package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val photoUri: String = "", // Holds "avatar_n" or custom local file uri
    val preferredCommType: String = "CALL", // "CALL", "WHATSAPP", "BOTH"
    val intervalDays: Int = 1, // e.g. 1 (every day), 2 (every 2 days), 7 (every week), 14 (every 2 weeks), 30 (every month)
    val snoozeMinutes: Int = 30, // 30 mins, 60 mins etc.
    val minCallDurationSeconds: Int = 30,
    val defaultWhatsAppMessage: String = "السلام عليكم، كيف حالك؟ ❤️",
    val notes: String = "",
    val isReminderEnabled: Boolean = true,
    val lastContactTimestamp: Long = 0L,
    val nextReminderTimestamp: Long = 0L
)
