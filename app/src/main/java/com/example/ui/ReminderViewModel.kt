package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.ReminderConfig
import com.example.data.ReminderRepository
import com.example.receiver.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = ReminderRepository(db.reminderDao())

    val config: StateFlow<ReminderConfig?> = repository.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val callHistory: StateFlow<List<CallRecord>> = repository.allCallRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent

    init {
        // Pre-create the single config row in the database if empty on launch
        viewModelScope.launch {
            if (repository.getConfig() == null) {
                repository.saveConfig(ReminderConfig())
            }
        }
    }

    fun saveSettings(
        contactName: String,
        phoneNumber: String,
        intervalDays: Int,
        snoozeMinutes: Int,
        minCallDurationSeconds: Int
    ) {
        viewModelScope.launch {
            val current = repository.getConfig() ?: ReminderConfig()
            val updated = current.copy(
                contactName = contactName,
                phoneNumber = phoneNumber,
                intervalDays = intervalDays,
                snoozeMinutes = snoozeMinutes,
                minCallDurationSeconds = minCallDurationSeconds
            )
            repository.saveConfig(updated)
            
            // Re-schedule alarm if enabled to apply settings
            if (updated.isReminderEnabled && updated.phoneNumber.isNotEmpty()) {
                val nextTrigger = ReminderScheduler.scheduleNextAlarm(getApplication(), updated)
                repository.saveConfig(updated.copy(nextReminderTimestamp = nextTrigger))
            }
            _toastEvent.emit("settings_saved")
        }
    }

    fun enableReminder() {
        viewModelScope.launch {
            val current = repository.getConfig()
            if (current == null || current.phoneNumber.trim().isEmpty()) {
                _toastEvent.emit("phone_number_required")
                return@launch
            }
            val updated = current.copy(isReminderEnabled = true)
            repository.saveConfig(updated)
            val nextTrigger = ReminderScheduler.scheduleNextAlarm(getApplication(), updated)
            repository.saveConfig(updated.copy(nextReminderTimestamp = nextTrigger))
            _toastEvent.emit("reminder_enabled")
        }
    }

    fun disableReminder() {
        viewModelScope.launch {
            val current = repository.getConfig() ?: return@launch
            val updated = current.copy(isReminderEnabled = false, nextReminderTimestamp = 0L)
            repository.saveConfig(updated)
            ReminderScheduler.cancelAlarm(getApplication())
            _toastEvent.emit("reminder_disabled")
        }
    }

    fun triggerTestNotification() {
        viewModelScope.launch {
            val current = repository.getConfig() ?: ReminderConfig()
            ReminderScheduler.showReminderNotification(getApplication(), current)
        }
    }

    fun confirmCallToday() {
        viewModelScope.launch {
            val current = repository.getConfig() ?: return@launch
            val now = System.currentTimeMillis()
            
            // 1. Save Call Record
            repository.addCallRecord(
                CallRecord(
                    timestamp = now,
                    durationSeconds = 0,
                    isManual = true,
                    callerName = current.contactName
                )
            )

            // 2. Update lastCallTimestamp
            val updated = current.copy(lastCallTimestamp = now)
            repository.saveConfig(updated)

            // 3. Re-schedule normal alarm
            val nextAlarm = ReminderScheduler.scheduleNextAlarm(getApplication(), updated)
            repository.saveConfig(updated.copy(nextReminderTimestamp = nextAlarm))
            
            _toastEvent.emit("call_confirmed_manual")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
