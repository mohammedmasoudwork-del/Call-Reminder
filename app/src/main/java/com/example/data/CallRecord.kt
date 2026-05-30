package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personId: Int,
    val personName: String, // Cached snapshot name for simplicity or safety if person is deleted
    val timestamp: Long = System.currentTimeMillis(),
    val commType: String = "CALL", // "CALL" or "WHATSAPP"
    val durationSeconds: Int = 0, // Used for automagic physical call log checks
    val isManual: Boolean = false // Was it manually confirmed or automatically checked?
)
