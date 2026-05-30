package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeopleFlow(): Flow<List<Person>>

    @Query("SELECT * FROM people")
    suspend fun getAllPeopleList(): List<Person>

    @Query("SELECT * FROM people WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Int): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllCallRecordsFlow(): Flow<List<CallRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord)

    @Query("DELETE FROM call_records")
    suspend fun clearCallHistory()

    @Query("DELETE FROM call_records WHERE personId = :personId")
    suspend fun clearCallHistoryForPerson(personId: Int)
}
