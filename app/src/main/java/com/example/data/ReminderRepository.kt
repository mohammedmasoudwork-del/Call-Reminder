package com.example.data

import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val reminderDao: ReminderDao) {

    val configFlow: Flow<ReminderConfig?> = reminderDao.getConfigFlow()

    suspend fun getConfig(): ReminderConfig? {
        return reminderDao.getConfig()
    }

    suspend fun saveConfig(config: ReminderConfig) {
        reminderDao.insertConfig(config)
    }

    val allCallRecords: Flow<List<CallRecord>> = reminderDao.getAllCallRecordsFlow()

    suspend fun addCallRecord(record: CallRecord) {
        reminderDao.insertCallRecord(record)
    }

    suspend fun clearHistory() {
        reminderDao.clearCallHistory()
    }
}
