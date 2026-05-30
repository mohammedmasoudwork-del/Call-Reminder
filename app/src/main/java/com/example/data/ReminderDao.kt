package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminder_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<ReminderConfig?>

    @Query("SELECT * FROM reminder_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): ReminderConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ReminderConfig)

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllCallRecordsFlow(): Flow<List<CallRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord)

    @Query("DELETE FROM call_records")
    suspend fun clearCallHistory()
}
